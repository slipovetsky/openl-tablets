package org.openl.ie.constrainer;

///////////////////////////////////////////////////////////////////////////////
/*
 * Copyright Exigen Group 1998, 1999, 2000
 * 320 Amboy Ave., Metuchen, NJ, 08840, USA, www.exigengroup.com
 *
 * The copyright to the computer program(s) herein
 * is the property of Exigen Group, USA. All rights reserved.
 * The program(s) may be used and/or copied only with
 * the written permission of Exigen Group
 * or in accordance with the terms and conditions
 * stipulated in the agreement/contract under which
 * the program(s) have been supplied.
 */
///////////////////////////////////////////////////////////////////////////////
/**
 * An implementation of a {@link Goal} that instaintiates the constraint integer variable.
 * <p>
 * It uses a Selector to select the next value to be removed from the domain of the variable.
 * <p>
 * The selector should select the values that can be <b>effectively</b> removed from the domain of the variable. For
 * example, if the domain type is plain then selector should select only min or max values from the domain.
 * GoalInstantiate can use both recursive and non-recursive search algorithms. Recursive implementation iterates the
 * domain looking for a value that can be assigned to a variable by GoalSetValue. Non-recursive implementations invokes
 * GoalSetValue ones and removes the given value if it fails.
 *
 */
public class GoalInstantiate extends GoalImpl {
    /**
     * An internal interface for the instantiation of the variable.
     */
    interface Impl extends java.io.Serializable {
        /**
         * Instantiate variable with a chosen_value.
         */
        Goal instantiate(int chosen_value) throws Failure;
    }

    /**
     * Recursive instantiation.
     */
    class RecursiveImpl implements Impl {
        private final GoalSetValue _goal_value;
        private GoalRemoveValue _goal_remove;
        private GoalSetMin _goal_min;
        private GoalSetMax _goal_max;

        public RecursiveImpl() {
            _goal_value = new GoalSetValue(_intvar);

            if (_intvar.domainType() != IntVar.DOMAIN_PLAIN) {
                _goal_remove = new GoalRemoveValue(_intvar);
            } else {
                _goal_min = new GoalSetMin(_intvar);
                _goal_max = new GoalSetMax(_intvar);
            }
        }

        @Override
        public Goal instantiate(int chosen_value) throws Failure {
            _goal_value.value(chosen_value);

            Goal goal_limit;
            if (_intvar.domainType() != IntVar.DOMAIN_PLAIN) {
                _goal_remove.value(chosen_value);
                goal_limit = _goal_remove;
            } else {
                if (chosen_value == _intvar.min()) {
                    _goal_min.min(chosen_value + 1);
                    goal_limit = _goal_min;
                } else {
                    _goal_max.max(chosen_value - 1);
                    goal_limit = _goal_max;
                }
            }
            // Debug.on();Debug.print(this + " by "+chosen_value);Debug.off();

            return new GoalOr(_goal_value, new GoalAnd(goal_limit, GoalInstantiate.this));
        }

    } // ~RecursiveImpl

    private final IntVar _intvar;

    private final Impl _impl;

    /**
     * Creates GoalInstantiate using <code>IntValueSelectorMin</code> as a default ValueSelector. The goal constructed
     * that way uses a recursive search algorithm.
     */
    public GoalInstantiate(IntVar intvar) {
        super(intvar.constrainer(), "");// "Instantiate("+intvar.name()+")");
        _intvar = intvar;
        _impl = new RecursiveImpl();
    }

    /**
     * An implementation of the instantiation algorithm for the integer variable using value selector.
     */
    @Override
    public Goal execute() throws Failure {
        if (_intvar.size() == 1) {
            return null;
        }

        int chosen_value = _intvar.min();

        return _impl.instantiate(chosen_value);
    }

    /**
     * Returns a String representation of this goal.
     *
     * @return a String representation of this goal.
     */
    @Override
    public String toString() {
        return "Instantiate(" + _intvar.name() + ")";
    }

} // ~GoalInstantiate
