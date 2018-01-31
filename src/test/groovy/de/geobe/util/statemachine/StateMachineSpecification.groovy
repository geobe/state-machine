/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018.  Georg Beier. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditionstart:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.geobe.util.statemachine

import spock.lang.Specification

/**
 * Created by georg beier on 22.01.2018.
 */
class StateMachineSpecification extends Specification {

    static enum State {
        START, S1, S2, S3, SG, END
    }

    static enum Event {
        Start, Intern, E1, E2, EG, Exit
    }

    StateMachine<State, Event> sm
    StringBuffer sbuf

    def enter = {
        sbuf.append(" enter ${sm.currentState}\n")
    }
    def exit = { sbuf.append("leave ${sm.currentState} ") }

    def setup() {
        sbuf = new StringBuffer()
        sm = new StateMachine<>(State.START, 'smtest')
        def transarg = { Object... args -> sbuf.append "--(${args[0]})->" }
        def trans = { sbuf.append "-->" }
        def transchange = { sbuf.append '-->'; State.S3 }
        def transintern = { Object... args ->
            sbuf.append "${sm.currentState} >-${args ? '(' + args + ')' : ''}-<\n"
        }

        sm.addEntryAction(State.S1, enter)
        sm.addEntryAction(State.S2, enter)
        sm.addEntryAction(State.S3, enter)
        sm.addEntryAction(State.END, enter)

        def action = { sbuf.append('-Starting->') }
        sm.addTransition(State.START, State.S1, Event.Start, action)
        sm.addTransition(State.S1, State.S2, Event.E1, trans)
        sm.addTransition(State.S2, State.S1, Event.E1, trans)
        sm.addInternalActivity(State.S1, Event.Intern, transintern)
        sm.addTransition(State.S1, State.S2, Event.E2, transarg)
        sm.addTransition(State.S2, State.S1, Event.E2, transchange)
        sm.addTransition(State.S3, State.S3, Event.E1, { sbuf.append '--loop->' })
        sm.addTransition(State.S3, State.S3, Event.E2, { sbuf.append '--end->'; State.END })
        sm.addTransition(State.END, State.S3, Event.E2, { ... args ->
            sbuf.append 'goto restart \n\n'; args[0]
        })

        sm.addExitAction(State.S1, exit)
        sm.addExitAction(State.S2, exit)
        sm.addExitAction(State.S3, exit)
        sm.addExitAction(State.START, { sbuf.append('(O)--') })

    }

    def cleanup() {
        println("\n${sbuf.toString()}")
    }

    def 'do nothing but initialize tests'() {
        when: 'we are here'
        then: 'sm exists'
        sm.currentState == State.START
    }

    def 'the sm should respect guarded transitions'() {
        given: 'some new guarded transitions'
        boolean guard
        int count = 0
        sm.addEntryAction(State.SG, enter)
        sm.addExitAction(State.SG, exit)
        sm.addGuardedTransition(State.S1, State.SG, Event.EG, { 42 })
        sm.addGuardedTransition(State.S2, State.SG, Event.EG, { true })
        sm.addGuardedTransition(State.SG, State.S1, Event.EG, { guard })
        sm.addGuardedTransition(State.SG, State.S2, Event.EG, { !guard })
        sm.addInternalActivity(State.SG, Event.Intern, { Object... args ->
            sbuf.append "${sm.currentState} >-${args ? '(' + args + ')' : ''}-<\n"
            count += args[0]
        }, { guard })
        when: 'sm goes START -Start-> S1 -EG{42}->'
        sm.execute(Event.Start)
        sm.execute(Event.EG)
        then: 'sm is in SG'
        sm.currentState == State.SG
        when: 'guard is true, --EG->'
        guard = true
        sm.execute(Event.EG)
        then: 'sm is in S1'
        sm.currentState == State.S1
        when: 'guard is false, --EG->'
        guard = false
        sm.execute(Event.EG)
        sm.execute(Event.EG)
        then: 'sm is in S2'
        sm.currentState == State.S2
        when: 'guard is true -Intern->'
        guard = true
        sm.execute(Event.EG)
        sm.execute(Event.Intern, 42)
        then: 'internal event/action is executed'
        count == 42
        sm.currentState == State.SG
        when: 'guard is not true '
        guard = false
        count = 0
        sm.execute(Event.Intern, 42)
        then: 'internal event/action is not executed'
        count == 0
        sm.currentState == State.SG
    }

//    @Ignore
    def 'the state machine should perform nicely'() {
        when: 'sm is created'
        then: 'it should be in Stert state'
        sm.currentState == State.START
        when: 'sm is started'
        sm.execute(Event.Start)
        then: 'sm is in S1'
        sm.currentState == State.S1
        when: 'E1 in S1'
        sm.execute(Event.E1)
        then: 'sm in S2'
        sm.currentState == State.S2
        when: 'E1 in S2'
        sm.execute(Event.E1)
        then: 'sm in S1'
        sm.currentState == State.S1
        when: 'Intern in S1'
        sm.execute(Event.Intern, 'Hallo Intern')
        then: 'sm in S1'
        sm.currentState == State.S1
        when: 'E2 with args in S1'
        sm.execute(Event.E2, 'Hello args', 'and much more...')
        then: 'sm in S2'
        sm.currentState == State.S2
        when: 'E2 in S2 with change'
        sm.execute(Event.E2)
        then: 'sm in S3'
        sm.currentState == State.S3
        when: 'E1 in S3 looping'
        sm.execute(Event.E1)
        then: 'sm in S3'
        sm.currentState == State.S3
        when: 'E2 in S3 with external change'
        sm.execute(Event.E2)
        then: 'sm in END'
        sm.currentState == State.END
        when: 'E2 in END with restart arg'
        sm.execute(Event.E2, State.START)
        then: 'sm in START'
        sm.currentState == State.START
    }

//    @Ignore
    def 'the sm should perform efficiently'() {
        when: 'the sm performs a loop'
        (0..5).each {
            sm.execute(Event.Start)
            sm.execute(Event.E1)
            sm.execute(Event.E1)
            sm.execute(Event.Intern)
            sm.execute(Event.E2, 'Hello args', 'and much more...')
            sm.execute(Event.E2)
            sm.execute(Event.E1)
            sm.execute(Event.E2)
            sm.execute(Event.E2, State.START)
        }
        then: 'sm in START'
        sm.currentState == State.START
    }
}
