require 'socket'
require 'pp'

class Portal
  class Error < StandardError; end
  class ReadError     < Error; end
  class ProtocolError < Error; end
  RESULT_WAIT = 0.01

  def initialize(port, host = "localhost")
    @socket = TCPSocket.new(host, port)
    @contexts = {}
    Thread.new do
      while (message = receive_message)
        id, type, content = message
        if ["stdout", "stderr"].include?(type)
          out = context(id)[type][1]
          out.write(content)
          out.flush
        else
          context(id)[:results] << [type, content]
        end
      end
    end
  end

  def send_message(id, type, content)
    message = "#{id} #{type} #{content}"
    @socket.write("#{message.size}:")
    @socket.write(message)
    @socket.write(",")
    @socket.flush
  end

  def receive_message
    size = ""
    while (c = @socket.getc) != ?:
      raise ProtocolError.new("Message size must be an integer, found #{c.chr}") unless (?0..?9).include?(c)
      size << c
    end
    message = @socket.read(size.to_i).split(/\s+/, 3)
    raise ProtocolError.new("Message must be followed by comma") unless @socket.getc == ?,
    message
  end

  def context(id)
    @contexts[id] ||= {
      :results => [],
      :count   => 0,
      :stdout  => IO.pipe,
      :stderr  => IO.pipe
    }
  end

  def with_context(id)
    old_id, @id = @id, id
    yield
  ensure
    @id = old_id
  end

  def eval(form, id = @id || rand)
    send_message(id, "eval", form)
    context = context(id.to_s)
    count   = context[:count] += 1;
    lambda do
      while (count > context[:results].size)
        sleep(RESULT_WAIT)
      end
      type, form = context[:results][count - 1]
      case type
      when "error"      then raise Error,     form
      when "read-error" then raise ReadError, form
      else form.split("\n")
      end
    end
  end
end
