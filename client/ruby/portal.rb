require 'socket'
require 'portal/platform'
require 'portal/jvm'
require 'pp'

class Portal
  class Error < StandardError; end
  class ProtocolError < Error; end
  RESULT_WAIT = 0.01
  BLOCK_SIZE  = 1024

  def initialize(port, host = "localhost")
    @socket = TCPSocket.new(host, port)
    @contexts = {}
    Thread.new do
      while (message = receive_message)
        id, type, content = message
        if ["stdout", "stderr"].include?(type)
          out = context(id)[type.to_sym][1]
          out.write(content)
          out.flush
        elsif ["result", "error", "read-error"].include?(type)
          context(id)[:results] << [type, content]
        else
          raise ProtocolError, "unknown message type: #{type}"
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
    message = @socket.read(size.to_i).split(/ /, 3)
    raise ProtocolError.new("Message must be followed by comma") unless @socket.getc == ?,
    message
  end

  def context(id)
    @contexts[id.to_s] ||= {
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
    context = context(id)
    count   = context[:count] += 1;
    lambda do
      while (count > context[:results].size)
        sleep(RESULT_WAIT)
      end
      type, form = context[:results][count - 1]
      if type == "result"
        form.split("\n")
      else
        vals = form.split("\n")
        vals[-1] = {type.to_sym => vals[-1]}
        vals
      end
    end
  end

  def write(string, id = @id)
    raise ProtocolError, "context id required to write to stdin" unless id
    send_message(id, "stdin", string)
  end

  def tail(type, id = @id)
    raise ProtocolError, "context id required to tail" unless id
    while true
      print(context(id)[type.to_sym][0].readpartial(BLOCK_SIZE))
    end
  rescue EOFError
  end
end
