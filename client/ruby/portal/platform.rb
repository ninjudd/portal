if RUBY_PLATFORM =~ /(mingw|mswin)(32|64)$/
  require 'rubygems'
  require 'win32/process'

  TERM = 1
  KILL = 'KILL'
  PATH_SEP = ';'

  USER_HOME = File.expand_path(ENV['HOMEDRIVE'] + ENV['HOMEPATH'])

  def daemon(cmd)
    Process.create(:app_name => cmd.join(' ')).process_id
  end
else
  TERM = 'TERM'
  KILL = 'KILL'
  PATH_SEP = ':'

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

begin
  require 'readline'
  READLINE = Readline.respond_to?(:emacs_editing_mode) ? :libedit : true
rescue LoadError
  READLINE = false
  module Readline
    HISTORY = []
    attr_accessor :basic_word_break_characters, :completion_proc
    def readline(prompt)
      $stdout.print_flush(prompt)
      $stdin.gets
    end
    extend Readline
  end
end
