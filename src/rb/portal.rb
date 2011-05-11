require 'socket'
require 'pp'

class Portal
  class ProtocolError < StandardError; end
  RESULT_WAIT = 0.01

  def initialize(port, host = "localhost")
    @socket = TCPSocket.new(host, port)
    @contexts = {}
    Thread.new do
      while (message = receive_message)
        id, type, form = message
        if [":stdout", ":stderr"].include?(type)
          out = context(id)[type][1]
          out.write(form)
          out.flush
        else
          context(id)[:results] << [type, form]
        end
      end
    end
  end

  def stringify(obj)
    obj.kind_of?(Symbol) ? ":#{obj}" : obj.to_s
  end

  def send_message(id, type, form)
    message = "#{stringify(id)} #{stringify(type)} #{form}"
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
    id, type, form = @socket.read(size.to_i).split(/\s+/, 3)
    raise ProtocolError.new("Message must be followed by comma") unless @socket.getc == ?,
    [id, type, form]
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
    id = id.to_s
    send_message(id, :eval, form)
    context = context(id)
    count   = context[:count] += 1;
    lambda do
      while (count > context[:results].size)
        sleep(RESULT_WAIT)
      end
      context[:results][count - 1]
    end
  end
end
