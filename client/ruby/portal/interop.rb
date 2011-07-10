class Object
  # Support converting simple Ruby data structures to Clojure expressions.
  def to_clj(unquote = false)
    if unquote
      case self
      when Array  then return collect {|i| i.to_clj(true)}.join(' ')
      when String then return self
      when nil    then return ''
      end
    end
    case self
    when Hash   then '{' + collect {|k,v| k.to_clj + ' ' + v.to_clj}.join(' ') + '}'
    when Array  then '[' + collect {|i| i.to_clj}.join(' ') + ']'
    when Symbol then ":#{to_s}"
    else             inspect.gsub(/(\\a|\\e|\\v|\\x|\\#)/) {|c| CLJ_SUB[c]}
    end
  end

  CLJ_SUB = {
    '\a' => '\007',
    '\e' => '\033',
    '\v' => '\013',
    '\#' => '#',
    '\x' => 'x', # This will mangle some strings in ruby 1.9.1, but it is better than breaking altogether.
  }

  define_method(:~) { Unquoted.new(self) }
end

class Unquoted
  attr_reader :object
  def initialize(object)
    @object = object
  end

  def to_clj(quote = false)
    object.to_clj(!quote)
  end
  alias to_s to_clj

  define_method(:~) { @object }
end
