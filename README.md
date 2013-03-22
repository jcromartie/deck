# deck

Data is music to your ears.

![an old school tape deck](http://jcromartie.github.com/deck/images/deck.jpg)

You think it would be pretty neat if you could just record everything
and and play it back later, but it would be even better if you could
remix it on the fly.

Well, now you can (and pretty easily if you ask me).

deck is designed to record and replay *events* on a piece of state.

Those events are appended to a file on disk

## Usage

Include deck as a dependency in your Leiningen `project.clj` with:

    [deck "0.1.0-SNAPSHOT"]

To use deck, you just give it some initial state and a path to store
events in.

    user=> (require '[deck.core :as deck])
    
    user=> (def db (deck/deck {} "./datastore"))

You extend the deck.core/play multimethod to implment your own custom
events that change your data's state.
    
    (defmethod deck/play ::add-user
      [state [_ name email]]
      (update-in state [:users]
        assoc (keyword name) {:name name :email email}))

Then you can record an event on the database like this:

    user=> (deck/record db [::add-user "john" "john@example.com"])
    user=> (assert (= "john" (-> @db :users :john :name)))

The change happens immediately in a transaction, and is written to
disk before `record` returns. Side note; your data file now looks like
this:

    ;; at Fri Mar 22 11:42:10 EDT 2013
    [:user/add-user "john" "john@example.com"]

You can reopen the db from disk and it will replay the events.

    user=> (def db (deck/deck {} "./datastore"))
    user=> (assert (= "john" (-> @db :users :john :name)))

You can redefine your methods on the fly (i.e. in the REPL or by
reloading namespaces), and replay events to see the new effect on your
data.

    (defmethod deck/play ::add-user
      [state [_ name email]]
      (update-in state [:users]
        ;; tack on :new-user true, too...
        assoc (keyword name) {:name name :email email :new-user true}))

Then just replay the events:

    user=> (deck/replay db)
    user=> (assert (:new-user (-> @db :users :john)))

## Performance

deck is not designed for truly high performance, but it does well
enough. On my 2.2 GHz i7, with a 5400 RPM hard drive, I can write
about 12K simple events/s, and replay 200K simple events/s.

deck is intended for use in situatons where your data model can fit in
memory (as an event stream and a "current" state).

In the future events could be stored and retrieved in different ways,
such as an RDBMS or HTTP.

The current implementation uses Clojure's refs and agents to handle
concurrency and serialize writes.

## License

Copyright © 2013 John Cromartie

Distributed under the MIT license.
