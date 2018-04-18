## Implementing UI Behavior with State Machines
UI behavior implementation can be much simplified with state machines.
This repository contains a simplified UML State Chart implementation in
Groovy and a sample base class to apply stateful behavior to typical
detail view and edit pages.

### StateMachine.groovy
A groovy implementation for a simple state machine with these features:
* States and Events are represented by Enums
* Activities and Guards are implemented as Closures
* Transitions can have Guards and Activities
  * they are defined with fromState, targetState, triggerEvent, \[Activity] and \[Guard]
  * if Activity returns a State, the default targetState is overwritten, thus allowing branching Transitions 
* States can have Activities onEntry, onExit and onEvent
  * onEvent Activities can also have Guards

### DetailViewBehavior.groovy
An abstract class for detail views controlled by a selection view 
like a Tree, TreeTable, List etc. .
The behaviour can be controlled efficiently by a state machine 
for detail views. See more details in the GroovyDoc

### Documentation
* [GroovyDoc](https://geobe.github.io/state-machine/docs/index.html)
* [State Machine Tutorial](https://www.georgbeier.de/docs-and-howtos/ui-state-machine/)

