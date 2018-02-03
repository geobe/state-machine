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

package de.geobe.util.statemachine.samples

import de.geobe.util.statemachine.StateMachine

/**
 * <p>A trait for detail views controlled by a selection view like a Tree, TreeTable, List etc. .
 * The behaviour can be controlled efficiently by a state machine for detail views.</p>
 * <p>The structure of the behavior model is implemented by states and transitions. The actual
 * behaviour is realised by actions (-> abstract methods) attached to transitions and entry of states.
 * These methods form a template structure to be filled by all implementing classes.</p>
 * <p>There are two slightly different types of detail views:</p>
 * <ol><li> TOPVIEW: Detail view(s) representing objects on the top level of an object hierarchy, 
 * usually on the root level of a tree view or represented in a list view have their
 * create button active even if no top level element is selected directly or indirectly via a subelement</li>
 * <li> SUBVIEW: Detail views representing objects below the top level have their create button only active when an
 * object of a higher level is directly or indirectly selected so that the newly created object can be attached
 * to this higher level ("parent") element.</li></ol>
 * <p>The behaviour model assumes that the selection cannot change during edit or create, so the selection component
 * (Tree, List etc.) should be disabled during edit or create. Drawn as a state chart, 
 * the behaviour model looks like this:</p>
 * <pre>
 *
 *            SUBVIEW               TOPVIEW
 *               O                     O
 *               |                     |
 *             <init>                <init>
 *               |                     |
 *           +---v---+           +-----v-----+
 *           | INIT  |---root--->|   EMPTY   |
 *           +-v-----+           +|-^-v--^---+
 *             |                  | | |  |
 *             |  +-----select----+ | |  |
 *        select  |  +------root----+ |  |
 *             |  |  |                |  |
 *     +----+  |  |  |           create cancel
 *    select|  |  |  |                |  |
 *     |  +-v--v--v--^--+       +-----v--^----+
 *     +-<|    SHOW     |<-save-| CREATEEMPTY |
 *        +v--^--^--v---+       +-------------+
 *         |  |  |  |
 *         |  |  +--|-------------+
 *         |  +--|--|----------+  |
 *         |  |  |  +-------+  |  |
 *         |  | cancel      |  | cancel
 *    create  |  |       edit  |  |
 *         |save |          |save |
 *       +-v--^--^-+      +-v--^--^-+
 *       | CREATE  |      |  EDIT   |
 *       +---------+      +---------+
 *
 * </pre>
 * @author georg beier
 */
abstract class DetailViewBehavior {
    protected StateMachine<DVState, DVEvent> sm

    /**
     * initialize the state machine with
     * @param initState
     */
    void initSm(DVState initState) {

        sm = new StateMachine<DVState, DVEvent>(initState)

        sm.addEntryAction(DVState.INIT, { clearFields(); initmode() })
        sm.addEntryAction(DVState.EMPTY, { emptymode() })
        sm.addEntryAction(DVState.SHOW, { showmode() })
        sm.addEntryAction(DVState.CREATEEMPTY, { createemptymode() })
        sm.addEntryAction(DVState.CREATE, { clearFields(); createmode() })
        sm.addEntryAction(DVState.EDIT, { editmode() })

        sm.addTransition(DVState.SUBVIEW, DVState.INIT, DVEvent.Init)
        sm.addTransition(DVState.INIT, DVState.SHOW, DVEvent.Select)
        sm.addTransition(DVState.INIT, DVState.EMPTY, DVEvent.Root)
        sm.addTransition(DVState.TOPVIEW, DVState.EMPTY, DVEvent.Init)
        sm.addTransition(DVState.EMPTY, DVState.EMPTY, DVEvent.Root)
        sm.addTransition(DVState.EMPTY, DVState.CREATEEMPTY, DVEvent.Create)
        sm.addTransition(DVState.EMPTY, DVState.SHOW, DVEvent.Select)
        sm.addTransition(DVState.CREATEEMPTY, DVState.SHOW, DVEvent.Save) {
            onCreateSave()
        }
        sm.addTransition(DVState.CREATEEMPTY, DVState.EMPTY, DVEvent.Cancel) {
            onCreateCancel()
        }
        sm.addTransition(DVState.SHOW, DVState.EDIT, DVEvent.Edit)
        sm.addTransition(DVState.SHOW, DVState.CREATE, DVEvent.Create)
        sm.addTransition(DVState.SHOW, DVState.SHOW, DVEvent.Select)
        sm.addTransition(DVState.SHOW, DVState.EMPTY, DVEvent.Root)
        sm.addTransition(DVState.EDIT, DVState.SHOW, DVEvent.Save) {
            saveItem()
            onEditDone()
        }
        sm.addTransition(DVState.EDIT, DVState.SHOW, DVEvent.Cancel) {
            setFieldValues()
            onEditDone()
        }
        sm.addTransition(DVState.CREATE, DVState.SHOW, DVEvent.Save) {
            onCreateSave()
        }
        sm.addTransition(DVState.CREATE, DVState.SHOW, DVEvent.Cancel) {
            onCreateCancel()
        }
    }

    void execute(DVEvent event, Object... params) {
        sm.execute(event, params)
    }

    /** prepare for editing in CREATEEMPTY state */
    protected abstract void createemptymode()
    /** prepare for editing in CREATE state */
    protected abstract void createmode()
    /** leaving CREATE or CREATEEMPTY state with save */
    protected void onCreateSave() {}
    /** leaving DIALOG state with cancel */
    protected void onCreateCancel() {}
    /** prepare for editing in EDIT state */
    protected abstract void editmode()
    /** prepare INIT state */
    protected void initmode() {}
    /** prepare EMPTY state */
    protected abstract void emptymode()
    /** prepare SHOW state */
    protected abstract void showmode()
    /** clear all editable fields */
    protected abstract void clearFields()
    /**
     * Initialize all fields for a new item to display or edit. Given is its id,
     * so its full dto can be addressed and requested by an appropriate service.
     * This method is usually called by the selector component.
     * @param itemId unique identifying key
     */
    abstract void initItem(Long itemId)
    /**
     * set all fields from the current full dto object
     */
    protected abstract void setFieldValues()

    /**
     * Save current item after editing to persistent storage,
     * typically by calling an appropriate service.
     */
    protected abstract void saveItem()

    /**
     * When editing an item was finished [Save] or cancelled [Cancel], notify the selector component
     * to enable it and eventually update the items changed caption
     * @param itemId identifies edited item
     * @param caption eventually updated caption of the edited item
     * @param mustReload Component must reload after new item was created
     *        or (tree-) structure changed
     */
    protected abstract void onEditDone(boolean mustReload = true)
}

/**
 * It is a choice of implementing classes, if editing existing items or creating new items
 * uses a dialog window (States DIALOG and CREATEDIALOG) or the same fields as for show and edit
 * (States EDIT, CREATE, CREATEEMPTY).<br>
 * States CREATEEMPTY ans DIALOGEMPTY were introduced, because the state model should completely
 * represent the state of the UI. So it can be avoided to have an additional implicit state represented
 * by a field that holds the current item or is empty.
 */
enum DVState {
    SUBVIEW,        // creation state for detail views of sublevel objects
    TOPVIEW,        // creation state for detail views of toplevel objects
    INIT,           // nothing is selected in the controlling tree
    EMPTY,          // no object is selected for this view, but a root node is selected
    SHOW,           // an object is selected and shown on the tab
    CREATEEMPTY,    // starting from EMPTY (important for Cancel events!), a new Object is created
    CREATE,         // starting from SHOW (important for Cancel events!), a new Object is created
    EDIT,           // selected object is being edited
}

enum DVEvent {
    Init,     // initialise state machine
    Select,   // an item of the displayed class was selected
    Root,     // a new branch was selected, either by selecting another top level object or some subobject
    Edit,     // start editing the selected object
    Create,   // start creating a new object
    Cancel,   // cancel edit or create
    Save,     // save newly edited or created object
}
