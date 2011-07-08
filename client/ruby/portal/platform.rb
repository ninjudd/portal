if RUBY_PLATFORM =~ /(mingw|mswin)(32|64)$/
  require 'rubygems'
  require 'win32/process'

  USER_HOME = File.expand_path(ENV['HOMEDRIVE'] + ENV['HOMEPATH'])

  def daemon(cmd)
    Process.create(:app_name => cmd.join(' ')).process_id
  end
else
  USER_HOME = File.expand_path(ENV['HOME'])
  class Process::Error; end

  def daemon(cmd)
    pid = fork do
      if not $stdin.tty?
        $stdout.close
        $stderr.close
      end
      Process.setsid
      exec(*cmd)
    end
    Process.detach(pid)
    pid
  end
end
