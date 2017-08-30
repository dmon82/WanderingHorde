# WanderingHorde
Wurm Unlimited mod that creates and controls groups of creatures (wip).

Some of the potential has been [shown in a video](https://www.youtube.com/watch?v=WJTJAl_RtHY) previously, but has not ended in a finished state. Some of the methods to control creatures turned out to be unreliable, and workarounds had to be taken. There was an extensive UI planned for setting up and controlling the hordes.

Key features included hordes walking along a one-way waypoints path, going back and forth, or in a circle. Paths would be calculated tile by tile using the existing A* at creation, because it's very CPU intensive, or re-calculated on demand if need be. Both land and sea creatures can use this system.

It would also be able to give them attack target, so a small pack of wolves could roam in an area and attack sheep. Sharks could roam around the server and attack dolphins.

On my local test server it handled over 1,000 creatures in a horde mostly fine, although I wouldn't recommend it as a standard size.

There are still irks where in some rare cases, creatures simply stop moving and it's hard very frustrating to debug.
