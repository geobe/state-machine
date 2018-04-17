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
 * furnished to do so, subject to the following conditions:
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
/**
 * Implementation of a state machine with these features
 * <ul><li>States as enums,</li>
 * <li>Events as enums,</li>
 * <li>Transitions are triggered by a combination of current state and event,</li>
 * <li>they can be guarded by a Closure inhibiting their execution,</li>
 * <li>onExit activities executed when leaving a state,</li>
 * <li>internal activities triggered by an event without state change.</li>
 * <li>onEntry activities executed when entering a state,</li>
 * <li>transitions executing activities after leaving a state and before entering a following state.
 * They can return a target state changing the following state.</li></ul>
 * @author georg beier
 * @param S enumertion Type for States. Limited by current implementation to 4095 values.
 * @param E enumeration type for events
 */
//@Slf4j
class StateMachine<S extends Enum, E extends Enum> {
    /** map of closures as entry actions */
    private Map<S, Closure> onEntry = new HashMap<>()
    /** map of closures as exit actions */
    private Map<S, Closure> onExit = new HashMap<>()
    /** Map of all transitions with guards and actions */
    private Map<Integer, List<TxDef<S>>> transitions = new HashMap<>()
    /** store current state */
    private S currentState
    /** for logging info to identify state machine instance */
    private String smid

    def getCurrentState() { currentState }

    def setSmId(String id) { smid = id }

    /**
     * create instance with initial fromState
     * @param start initial fromState
     * @param id identifying string for logging and debug
     */
    StateMachine(S start, String id = 'default') {
        currentState = start
        smid = id
    }

    /**
     * Add a transition to the state machine linking two states. It is not guarded, i.e. its guard is always true.
     * @param from the state, from where transition starts
     * @param to the state, where transition leads to. May be same as from state.
     * @param ev the event that triggers this transition
     * @param action (optional, default is do nothing) Closure that is executed during the transition. It may return
     * a different target state that overwrites to parameter. Thus branching transitions can be easily implemented.
     * Closure may have parameters of type Object...
     */
    void addTransition(S from, S to, E ev, Closure action = {}) {
        addGuardedTransition(from, to, ev, { true }, action)
    }

    /**
     * Add an "internal" transition that does not leave its state but executes some activity.
     * @param inState State in which this transition may happen
     * @param ev the event that triggers this transition
     * @param action Closure that is executed during the transition. Closure may have parameters
     * of type Object... .
     * @param guard (optional, default true) Closure that controls execution of the activity.
     * Only when it returns (Groovy-) true, activity is executed
     */
    void addInternalActivity(S inState, E ev, Closure action, Closure guard = { true }) {
        addGuardedTransition(inState, null, ev, guard, action)
    }

    /**
     * Add a transition to the state machine linking two states
     * @param from the state, from where transition starts
     * @param to the state, where transition leads to. May be same as from state.
     * @param ev the event that triggers this transition
     * @param guard Closure that controls execution of the transition. Only when it returns (Groovy-) true,
     * transition is executed
     * @param action Closure that is executed during the transition. It may return a different target state that
     * overwrites to parameter. Thus branching transitions can be easily implemented. Closure may have parameters
     * of type Object... .
     */
    void addGuardedTransition(S from, S to, E ev, Closure guard, Closure action = {}) {
        def index = trix(from, ev)
        if (!transitions[index]) {
            transitions[index] = []
        }
        TxDef<S> tx = new TxDef<>([start: from, target: to, action: action, guard: guard])
        transitions[index].add tx
    }

    /**
     * Add an onEntry activity to the state machine that is executed each time a transition enters
     * the state, even if it comes from the same state.
     * @param state which gets the activiy. Only one entry activity per state is supported
     * @param action Closure that implements the activity. Must not have parameters.
     */
    void addEntryAction(S state, Closure action) {
        onEntry[state] = action
    }

    /**
     * Add an onExit activity to the state machine that is executed each time a transition leaves
     * the state, even if it leads back to the same state.
     * @param state which gets the activiy. Only one exit activity per state is supported
     * @param action Closure that implements the activity. Must not have parameters.
     */
    void addExitAction(S state, Closure action) {
        onExit[state] = action
    }

    /**
     * Actions are identified by currentState and incoming event.
     * Execute first matching action with a guard delivering (Groovy-) true.
     * If none is found, no action is executed and state stays unchanged.
     * After execution, statemachine will be
     * <ul><li>in the following state as defined in addTransition method,
     * if closure returns no object of type S</li>
     * <li>in the state returned by the closure.</li>
     * <li>If no following state is defined, statemachine will stay in currentState and
     * no exitAction should have been executed.</li></ul>
     * @param event triggering event
     * @param params optional parameter to Action.act.
     *        Caution, Action.act will receive an Object[] Array
     * @return the current state after execution
     */
    S execute(E event, Object... params) {
        def index = trix(currentState, event)
        if (transitions[index]) {
//            S fromState = currentState
            List<TxDef<S>> txlist = transitions[index]
            // find first with guard evaluating to true
            TxDef<S> tx = txlist.find {
                it?.guard() ? true : false
            }
            if (tx?.target) {
                // full transition
                onExit[tx.start]?.call()
                def result = tx.action?.call(params)
                def next
                if (result instanceof S) {
                    next = result
                } else {
                    next = tx.target
                }
                currentState = next
                onEntry[next]?.call()
//                log.debug("Transition $smid: $fromState--$event->$next")
            } else {
                // inner transition
                tx?.action?.call(params)
//                log.debug("Inner Transition $smid: $fromState--$event->$currentState")
            }
        } else {
//            log.warn("ignored event $event in fromState $currentState")
        }
        currentState
    }

    /**
     * calculate a unique transition index from current state and triggering event
     * @param st current state
     * @param ev event triggering transition
     * @return a unique Integer computed from state and event
     */
    private static Integer trix(S st, E ev) {
        def t = st.ordinal() + (ev.ordinal() << 12)
        t
    }

    /**
     * supporting data structure to store transition information
     * @param <S> the state enumeration type
     */
    private static class TxDef<S> {
        S start
        S target
        Closure guard = {}
        Closure action = {}
    }
}

