
Conveys a piece of data between one producer thread and arbitrarily many consumer threads. The producer may at any time call
setSerialized(byte[]) to publish an object in serialized form. After that any consumer may call getDeserialized() any number of
times and it will retrieve the deserialized object.

<h3>Assumptions on the use case</h3>

The producer thread has many responsibilities, its time spent at any one task must be minimized.
Deserialization is expensive, therefore the producer must be relieved from it.
The usage pattern is read-heavy: there are many more invocations of getDeserialized() than of setSerialized(byte[]).
Each returned instance will probably be retained on the heap for a long time.


<h3>Characteristics of the implementation</h3>
<ol>
<li>Deserialization is lazy: if no invocation of getDeserialized() is
made, then no deserialization happens.
</li>
<li>The value deserialized by a consumer is cached and shared with other
consumers.
</li>
<li>The invocations of getDeserialized() will return at most as many
distinct instances as there were invocations of
setSerialized(byte[]).
</li>
<li>setSerialized(byte[]) is wait-free: it always completes in a finite
number of steps, regardless of any concurrent invocations of
getDeserialized().
</li>
<li>getDeserialized() is wait-free with respect to
setSerialized(byte[]): the producer thread may do anything, such
as calling setSerialized(byte[]) at a very high rate or getting
indefinitely suspended within an invocation, without affecting the ability
of getDeserialized() to complete in a finite number of steps.
</li>
<li>getDeserialized() is also wait-free against itself (concurrent
invocations don't interfere with each other), with one allowed exception:
when it observes a new serialized value, it may choose to block some of
the other invocations of getDeserialized() until it completes.
More formally, after the following sequence of events has occurred:
<ol>
    <li>the producer completes its last invocation of setSerialized(byte[]);
    </li>
    <li>a consumer starts an invocation of getDeserialized();
    </li>
    <li>the invocation completes by returning the object deserialized from the
    blob set by that last invocation,
    </li>
</ol>
all future invocations of getDeserialized() are wait-free.
</li>
<li>getDeserialized() exhibits (at least)  <em>eventually consistent,
monotonic read</em> behavior: once a consumer has observed an object derived
from a serialized value S, it will never observe an object derived from a
serialized value older than S, nor will it observe the initial {@code null}
value. If at any point the producer invokes setSerialized(byte[])
for the last time and the consumer keeps invoking getDeserialized(),
eventually it will return an object deserialized from that final producer's
invocation.
</li>
</ol>
