Portal is a unified REPL and command server for Clojure.

# Protocol

Portal uses netstrings to encode messages [^1]. This make client implementations as simple as
possible. The format of an individual message is as follows:

    id type data

The `id` is a string identifier used to determine which evaluation context to use. The context is
used to maintain state across multiple commands and has a unique stdin, stdout and stderr from other
contexts. It is helpful to think of each context as executing in a dedicated thread, though actually
agents are used to implement contexts, so a thread pool is shared among all contexts.

In addition, all commands within a given context are serialized so they happen in the order they are
received, and one command cannot start until the previous command for that context finishes. Of
course, you can call commands asynchronously though by using unique ids.

For client requests `type` can be one of:

* `eval` - Evaluate the forms provided in `data`.
* `stdin` - Write the string provided in `data` to `*in*`.
* `clear` - Clear the context for `id`.
* `fork` - Associate the context for `id` with the id provided in `data`.

For server responses `type` can be one of:

* `result` - Evaluation completed with no errors. The results are provided in `data`
* `error` - There was an exception during evaluation. The error type and message are provided in `data`.
* `read-error` - There was an error while reading the forms. The error message is provided in `data`.
* `stdout` - The string in `data` was printed to `*out*` during evaluation.
* `stderr` - The string in `data` was printed to `*err*` during evaluation.

# Client Libraries

* Ruby
* Clojure (coming soon)
* elisp (coming soon)
* Haskell (coming soon)
* Your favorite language (patches welcome...)

[^1]: Thanks to James Reeves for this suggestion.