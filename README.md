Portal is a unified REPL and command server for Clojure.

# Protocol

Portal uses [netstrings](http://en.wikipedia.org/wiki/Netstring) to encode messages<sup><a
name="ref1" href="#fn1">1</a></sup>. This allows client implementations to be as simple as possible.

A netstring consists of prefix with the byte length of the message that follows. The prefix is
separated from the message by a colon and each message is separated by a comma. Here is a simple
netstring:

    18:The cake is a lie!,

The format of an individual portal message is as follows:

    ID TYPE DATA

Where each element is separated by a single space.

#### ID

`ID` is a string identifier used to determine which evaluation context to use. The context is
used to maintain state across multiple commands and has a unique stdin, stdout and stderr from other
contexts. It is helpful to think of each context as executing in a dedicated thread, though actually
agents are used to implement contexts, so a thread pool is shared among all contexts.

In addition, all commands within a given context are serialized so they happen in the order they are
received, and one command cannot start until the previous command for that context finishes. Of
course, you can call commands asynchronously though by using unique ids.

#### TYPE

For client requests `TYPE` can be one of:

    eval  - Evaluate the forms provided in DATA. Also subscribes you to *out* and *err* for ID.
    stdin - Write the string provided in DATA to *in*.
    close - Close the context for ID and unsubscribe from *out* and *err*.
    fork  - Associate the context for ID with the id provided in DATA.

For server responses `TYPE` can be one of:

    result     - Evaluation completed with no errors. The results are provided in DATA.
    error      - There was an exception during evaluation. The error type and message are provided in DATA.
    read-error - There was an error while reading the forms. The error message is provided in DATA.
    stdout     - The string in DATA was printed to *out* during evaluation.
    stderr     - The string in DATA was printed to *err* during evaluation.

# Client Libraries

* Ruby
* Clojure (coming soon)
* elisp (coming soon)
* Haskell (coming soon)
* Javascript (coming soon)
* Your favorite language (patches welcome...)

<hr>

1. Thanks to [James Reeves](https://github.com/weavejester) for this suggestion. <a name="fn1" href="#ref1">&#8617;</a>
