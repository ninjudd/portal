require 'portal'
require 'portal/platform'
require 'pp'

class Portal
  class JVM
    attr_reader :args, :pidfile, :pid, :port

    def initialize(pidfile, *args)
      @pidfile = pidfile
      @args    = args
      refresh
      Process.kill(0, @pid)                            # make sure pid is valid
      TCPSocket.new("localhost", @port).close if @port # make sure jvm is running on port
    rescue Errno::ECONNREFUSED
      kill(true) # connection refused
    rescue Errno::ENOENT, Errno::ESRCH, Errno::ECONNREFUSED, Errno::EBADF, Errno::EPERM, Process::Error => e
      reset! # no pidfile or invalid pid
    end

    def refresh
      _, @pid, @port = IO.read(pidfile).split("\n").collect {|n| n.to_i}
    end

    def kill(force = false)
      if @pid
        signal = force ? KILL : TERM
        Process.kill(signal, @pid)
        reset!
      end
    end

    def cmd
      @cmd ||= ["java", args, "-Dportal.pidfile=#{pidfile}", "clojure.main", "-e",
                "(require 'portal.jvm) (portal.jvm/init)"].flatten.compact
    end

    def start
      return if @pid

      @pid = daemon(cmd)
      File.open(pidfile, 'w') {|f| f.write("#{cmd.join(' ')}\n#{@pid}\n")}

      while @port.nil?
        sleep 0.1
        refresh
      end
    end

    def portal
      @portal ||= Portal.new(@port)
    end

  private

    def reset!
      File.unlink(pidfile) if File.exists?(pidfile)
      @pid, @port = []
    end

  end
end
