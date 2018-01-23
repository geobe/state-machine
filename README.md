## State Machine
A groovy implementation for a simple state machine with these features:
* States and Events are represented by Enums
* Activities and Guards are implemented as Closures
* Transitions can have Guards and Activities
  * they are defined with fromState, targetState, triggerEvent, \[Activity] and \[Guard]
  * if Activity returns a State, the default targetState is overwritten, thus allowing branching Transitions 
* States can have Activities onEntry, onExit and onEvent
  * onEvent Activities can also have Guards

### Usage
These simple State Machines were found very useful for controlling user interface behaviour.

