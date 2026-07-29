# System Design

![System design](images/system-design.png)

This is the high-level picture for how this would scale: client hits an API gateway,
the gateway spreads requests across app instances, and the app instances talk to a
sharded Postgres setup underneath.

What's actually running right now is one instance against one database — no gateway,
no sharding. This is the direction I'd take it in if I needed to scale it, and the
reasoning behind each piece of it is below. The application-level design — what's
actually built, controllers/services/cache/db — is a separate write-up:
[APPLICATION_DESIGN.md](APPLICATION_DESIGN.md).

## Why an API gateway

I didn't want the URL shortener service itself responsible for load balancing,
authentication, and rate limiting. If it's doing all of that on top of its actual job,
every instance is carrying logic that has nothing to do with shortening URLs, and it
gets harder to change one without touching the other.

So that work sits in front, at the gateway. The gateway talks to the app instances,
and if one instance goes down, it can route to another one that's up. I'm not getting
into which load balancing strategy (round robin, least connections, etc.) — that's a
separate decision and not really what this exercise is about.

## Why multiple instances

If write volume goes up — more people creating short URLs at once — a single instance
eventually runs out of room, and you can't always just make one machine bigger.
Vertical scaling has a ceiling. So instead, run more of the same instance side by
side. This only works cleanly if the instances are stateless, which they are here —
any instance can handle any request, nothing is pinned to a specific one.

## Why sharding

Once there's more than one app instance, it doesn't make sense for all of them to
keep hitting a single database — that database becomes the actual bottleneck, and it
has its own ceiling on vertical scaling too. So the database needs to scale out as
well, which means splitting the data across multiple databases (shards).

That raises the real question: once a row is written to *some* shard, how does a
later read know which one to look in? The answer I landed on: derive the shard number
from the short code itself, and make sure the short code always carries that
information.

**Worked example.** Say there are 16 shards, so a shard number fits in 4 bits.

Write: a new row gets id `5001` from an internal counter, and this write lands on
shard `2`. Combine them into one number: shift the counter left 4 bits and OR in the
shard number.

```
id = (5001 << 4) | 2 = 80018
```

Base62-encode `80018` and that's the short code the user gets: `kOC`.

Read: someone requests `/kOC`. Decode it back to `80018`. Mask the last 4 bits to get
the shard number, and shift right 4 bits to get the original counter back.

```
80018 & 0b1111 = 2      → shard number
80018 >> 4     = 5001   → original id
```

So any instance, given just the code, knows which shard to query without asking
anyone or looking anything up — the shard number is baked into the code. Every
instance is connected to every shard, but a single request only ever touches the one
its code decodes to.

## What's not built yet

- Everything above this line — gateway, multiple instances, sharding. What's actually
  running is a single instance, single database. See
  [APPLICATION_DESIGN.md](APPLICATION_DESIGN.md) for what that single instance actually
  looks like.
