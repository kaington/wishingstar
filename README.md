
# WishingStar

Paper plugin that adds a big spinning nether star floating above a lodestone. You can grab it, throw it into the sky and make a wish.

## What it does

- Star floats and spins above a lodestone with some particles
- Right click it and it flies into your hand
- Right click while holding it and it goes up into the sky and waits for your wish
- Type your wish in chat and it explodes into fireworks and flies away
- If you don't wish anything in 90 seconds it just drifts away

## Requirements

- Paper 1.21+ (Purpur etc should work too)
- Java 21

## Install

Download the jar from Releases and put it in your `plugins` folder, then restart.

## Commands

- `/wishingstar` - places a star on the block you're looking at
- `/wishingstar <size>` - same but custom size (default is 1.8)
- `/wishingstar give` - gives you a star
- `/wishingstar remove` - removes the closest star

You can use `/ws` instead. Commands need `wishingstar.admin` (op by default), everything else works for everyone.

You can put a star back by right clicking the empty lodestone while holding it.

Stars don't save after a restart so you have to place them again.

## Building

```
gradle build
```
