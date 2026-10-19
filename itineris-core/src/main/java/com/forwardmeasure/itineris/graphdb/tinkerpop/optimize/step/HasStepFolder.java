// Copyright 2017 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step;

import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.lambda.ValueTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.HasContainerHolder;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.HasStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.OrStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.RangeGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.TraversalFilterStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.FlatMapStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.NoOpBarrierStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.OrderGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertiesStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.IdentityStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ElementValueComparator;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.EmptyStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.util.AndP;
import org.apache.tinkerpop.gremlin.process.traversal.util.ConnectiveP;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.graphdb.query.ItinerisPredicateUtils;
import com.forwardmeasure.itineris.graphdb.query.QueryUtil;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.ItinerisTraversalUtil;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public interface HasStepFolder<S, E> extends Step<S, E> {

    /**
     * Optional method which provides a hint to future has containers additions.
     * The method will optionally resize the underlying array for future addition to ensure that it can hold additional
     * elements. I.e. it will ensure that we can add `additionalSize` containers without any resizes.
     */
    void ensureAdditionalHasContainersCapacity(int additionalSize);

    void addHasContainer(final HasContainer hasContainer);

    List<HasContainer> addLocalHasContainersConvertingAndPContainers(TraversalParent parent, List<HasContainer> localHasContainers);

    List<HasContainer> addLocalHasContainersSplittingAndPContainers(TraversalParent parent, Iterable<HasContainer> has);

    void orderBy(String key, Order order);

    void localOrderBy(TraversalParent parent, List<HasContainer> hasContainers, String key, Order order);

    void setLimit(int low, int high);

    void setLocalLimit(TraversalParent parent, List<HasContainer> hasContainers, int low, int high);

    int getLowLimit();

    int getLocalLowLimit(TraversalParent parent, List<HasContainer> hasContainers);

    int getHighLimit();

    int getLocalHighLimit(TraversalParent parent, List<HasContainer> hasContainers);

    static boolean validItinerisHas(HasContainer has) {
        if (has.getPredicate() instanceof ConnectiveP) {
            final List<? extends P<?>> predicates = ((ConnectiveP<?>) has.getPredicate()).getPredicates();
            return predicates.stream().allMatch(p-> validItinerisHas(new HasContainer(has.getKey(), p)));
        } else {
            return ItinerisPredicateUtils.supports(has.getBiPredicate());
        }
    }

    static boolean validItinerisHas(Iterable<HasContainer> has) {
        for (final HasContainer h : has) {
            if (!validItinerisHas(h)) return false;
        }
        return true;
    }

    static boolean validItinerisOrder(OrderGlobalStep orderGlobalStep, Traversal rootTraversal,
                                          boolean isVertexOrder) {
        final List<Pair<Traversal.Admin, Object>> comparators = orderGlobalStep.getComparators();
        for(final Pair<Traversal.Admin, Object> comp : comparators) {
            final String key;
            if (comp.getValue0() instanceof ValueTraversal &&
                comp.getValue1() instanceof Order) {
                key = ((ValueTraversal) comp.getValue0()).getPropertyKey();
            } else if (comp.getValue1() instanceof ElementValueComparator) {
                final ElementValueComparator evc = (ElementValueComparator) comp.getValue1();
                if (!(evc.getValueComparator() instanceof Order)) return false;
                key = evc.getPropertyKey();
            } else {
                // do not fold comparators that include nested traversals that are not simple ElementValues
                return false;
            }
            final ItinerisTransaction tx = ItinerisTraversalUtil.getTx(rootTraversal.asAdmin());
            final PropertyKey pKey = tx.getPropertyKey(key);
            if (pKey == null
                || !(Comparable.class.isAssignableFrom(pKey.dataType()))
                || (isVertexOrder && pKey.cardinality() != Cardinality.SINGLE)) {
                return false;
            }
        }
        return true;
    }

    static void foldInIds(final HasStepFolder itinerisStep, final Traversal.Admin<?, ?> traversal) {
        Step<?, ?> currentStep = itinerisStep.getNextStep();
        while (true) {
            if (currentStep instanceof HasContainerHolder) {
                final HasContainerHolder hasContainerHolder = (HasContainerHolder) currentStep;
                final GraphStep graphStep = (GraphStep) itinerisStep;
                // HasContainer collection that we get back is immutable so we keep track of which containers
                // need to be deleted after they've been folded into the ItinerisStep and then remove them from their
                // step using HasContainer.removeHasContainer
                final List<HasContainer> removableHasContainers = new ArrayList<>();
                final Set<String> stepLabels = currentStep.getLabels();
                hasContainerHolder.getHasContainers().forEach(hasContainer -> {
                    if (GraphStep.processHasContainerIds(graphStep, hasContainer)) {
                        stepLabels.forEach(itinerisStep::addLabel);
                        // this has container is no longer needed because its ids will be folded into the ItinerisStep
                        removableHasContainers.add(hasContainer);
                    }
                });

                if (!removableHasContainers.isEmpty()) {
                    removableHasContainers.forEach(hasContainerHolder::removeHasContainer);
                }
                // if all has containers have been removed, the current step can be removed
                if (hasContainerHolder.getHasContainers().isEmpty()) {
                    traversal.removeStep(currentStep);
                }
            }
            else if (currentStep instanceof IdentityStep || currentStep instanceof NoOpBarrierStep) {
                // do nothing, has no impact
            } else {
                break;
            }
            currentStep = currentStep.getNextStep();
        }
    }

    static void foldInHasContainer(final HasStepFolder itinerisStep, final Traversal.Admin<?, ?> traversal,
            final Traversal<?, ?> rootTraversal) {
        Step<?, ?> currentStep = itinerisStep.getNextStep();
        while (true) {
            if (currentStep instanceof OrStep && itinerisStep instanceof ItinerisStep) {
                for (final Traversal.Admin<?, ?> child : ((OrStep<?>) currentStep).getLocalChildren()) {
                    if (!validFoldInHasContainer(child.getStartStep(), false)){
                        return;
                    }
                }
                ((OrStep<?>) currentStep).getLocalChildren().forEach(t ->localFoldInHasContainer(itinerisStep, t.getStartStep(), t, rootTraversal));
                currentStep.getLabels().forEach(itinerisStep::addLabel);
                traversal.removeStep(currentStep);
            } else if (currentStep instanceof HasContainerHolder && validFoldInHasContainer(currentStep, true)){
                List<HasContainer> hasContainersList = ((HasContainerHolder) currentStep).getHasContainers();
                itinerisStep.ensureAdditionalHasContainersCapacity(hasContainersList.size());
                for(HasContainer hasContainer : hasContainersList){
                    itinerisStep.addHasContainer(ItinerisPredicateUtils.convert(hasContainer));
                }
                currentStep.getLabels().forEach(itinerisStep::addLabel);
                traversal.removeStep(currentStep);
            } else if (isExistsStep(currentStep)) {
                currentStep = foldInHasFilter(traversal, (TraversalFilterStep) currentStep);
                continue;
            } else if (!(currentStep instanceof IdentityStep) && !(currentStep instanceof NoOpBarrierStep)) {
                break;
            }
            currentStep = currentStep.getNextStep();
        }
    }

    static void localFoldInHasContainer(final HasStepFolder itinerisStep, final Step<?, ?>  tinkerpopStep, final Traversal.Admin<?, ?> traversal,
            final Traversal<?, ?> rootTraversal){
        Step<?, ?> currentStep = tinkerpopStep;
        while (true) {
            if (currentStep instanceof HasContainerHolder) {
                List<HasContainer> localHasContainers = itinerisStep.addLocalHasContainersConvertingAndPContainers(
                    traversal.getParent(), ((HasContainerHolder) currentStep).getHasContainers());
                currentStep.getLabels().forEach(itinerisStep::addLabel);
                traversal.removeStep(currentStep);
                currentStep = foldInOrder(itinerisStep, currentStep, traversal, rootTraversal, itinerisStep instanceof ItinerisStep && ((ItinerisStep)itinerisStep).returnsVertex(), localHasContainers);
                foldInRange(itinerisStep, currentStep, traversal, localHasContainers);
            } else if (isExistsStep(currentStep)) {
                currentStep = foldInHasFilter(traversal, (TraversalFilterStep) currentStep);
                continue;
            } else if (!(currentStep instanceof IdentityStep) && !(currentStep instanceof NoOpBarrierStep)) {
                break;
            }
            currentStep = currentStep.getNextStep();
        }
    }

    static boolean validFoldInHasContainer(final Step<?, ?>  tinkerpopStep, final boolean defaultValue){
        Step<?, ?> currentStep = tinkerpopStep;
        Boolean toReturn = null;
        while (!(currentStep instanceof EmptyStep)) {
            if (currentStep instanceof HasContainerHolder) {
                final Iterable<HasContainer> containers = ((HasContainerHolder) currentStep).getHasContainers();
                toReturn = toReturn == null ? validItinerisHas(containers) : toReturn && validItinerisHas(containers);
            } else if (isExistsStep(currentStep)) {
                toReturn = true;
            } else if (!(currentStep instanceof IdentityStep) && !(currentStep instanceof NoOpBarrierStep) && !(currentStep instanceof RangeGlobalStep) && !(currentStep instanceof OrderGlobalStep)) {
                toReturn = toReturn != null && (toReturn && defaultValue);
                break;
            }
            currentStep = currentStep.getNextStep();
        }
        return Boolean.TRUE.equals(toReturn);
    }

    /**
     * Check if a given step is an "exists" step, i.e. has(key) step
     * Tinkerpop translates has(key) gremlin query into TraversalFilterStep(PropertiesStep)
     * @param step
     * @return true if given step is essentially an "exists" step
     */
    static boolean isExistsStep(final Step<?, ?> step) {
        if (step instanceof TraversalFilterStep) {
            List<Traversal.Admin> traversals = ((TraversalFilterStep) step).getLocalChildren();
            assert traversals.size() == 1;
            List<Step> steps = traversals.get(0).getSteps();
            if (!(steps.size() == 1 && steps.get(0) instanceof PropertiesStep)) return false;
            return ((PropertiesStep) steps.get(0)).getPropertyKeys().length == 1;
        }
        return false;
    }

    /**
     * Convert a TraversalFilterStep that is essentially "has(key)" into a normal HasStep
     * @param traversal local traversal
     * @param currentStep
     */
    static Step foldInHasFilter(final Traversal.Admin<?, ?> traversal, final TraversalFilterStep<?> currentStep) {
        List<? extends Traversal.Admin<?, ?>> traversals = currentStep.getLocalChildren();
        assert(traversals.size() == 1);
        List<Step> steps = traversals.get(0).getSteps();
        assert(steps.size() == 1 && steps.get(0) instanceof PropertiesStep);
        String[] propertyKeys = ((PropertiesStep) steps.get(0)).getPropertyKeys();
        assert(propertyKeys.length == 1);
        HasStep hasStep = new HasStep(traversal, new HasContainer(propertyKeys[0], P.neq(null)));
        currentStep.getLabels().forEach(hasStep::addLabel);
        TraversalHelper.replaceStep(currentStep, hasStep, traversal);
        return hasStep;
    }

    static Step<?, ?> foldInOrder(final HasStepFolder itinerisStep, final Step<?, ?>  tinkerpopStep, final Traversal.Admin<?, ?> traversal,
                                   final Traversal<?, ?> rootTraversal, boolean isVertexOrder, final List<HasContainer> hasContainers) {
        Step<?, ?> currentStep = tinkerpopStep;
        OrderGlobalStep<?, ?> lastOrder = null;
        while (true) {
            if (currentStep instanceof OrderGlobalStep) {
                if (lastOrder != null) { //Previous orders are rendered irrelevant by next order (since re-ordered)
                    lastOrder.getLabels().forEach(itinerisStep::addLabel);
                    traversal.removeStep(lastOrder);
                }
                lastOrder = (OrderGlobalStep) currentStep;
            } else if (!(currentStep instanceof IdentityStep) && !(currentStep instanceof HasStep) && !(currentStep instanceof NoOpBarrierStep)) {
                break;
            }
            currentStep = currentStep.getNextStep();
        }

        if (lastOrder != null && validItinerisOrder(lastOrder, rootTraversal, isVertexOrder)) {
            //Add orders to HasStepFolder
            for (final Pair<Traversal.Admin<Object, Comparable>, Comparator<Object>> comp : (List<Pair<Traversal.Admin<Object, Comparable>, Comparator<Object>>>) ((OrderGlobalStep) lastOrder).getComparators()) {
                final String key;
                final Order order;
                if (comp.getValue0() instanceof ValueTraversal) {
                    final ValueTraversal evt = (ValueTraversal) comp.getValue0();
                    key = evt.getPropertyKey();
                    order = (Order) comp.getValue1();
                } else {
                    final ElementValueComparator evc = (ElementValueComparator) comp.getValue1();
                    key = evc.getPropertyKey();
                    order = (Order) evc.getValueComparator();
                }
                if (hasContainers == null) {
                    itinerisStep.orderBy(key, order);
                } else {
                    itinerisStep.localOrderBy(traversal.getParent(), hasContainers, key, order);
                }
            }
            lastOrder.getLabels().forEach(itinerisStep::addLabel);
            traversal.removeStep(lastOrder);
        }
        return currentStep;
    }

    static void splitAndP(final List<HasContainer> hasContainers, final HasContainer hasContainer) {
        if (hasContainer.getPredicate() instanceof AndP) {
            for (final P<?> predicate : ((AndP<?>) hasContainer.getPredicate()).getPredicates()) {
                hasContainers.add(new HasContainer(hasContainer.getKey(), predicate));
            }
        } else {
            hasContainers.add(hasContainer);
        }
    }

    class OrderEntry {

        public final String key;
        public final Order order;

        public OrderEntry(String key, Order order) {
            this.key = key;
            this.order = order;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final OrderEntry that = (OrderEntry) o;

            if (!Objects.equals(key, that.key)) return false;
            return order == that.order;
        }

        @Override
        public int hashCode() {
            int result = key != null ? key.hashCode() : 0;
            result = 31 * result + (order != null ? order.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "OrderEntry{" +
                "key='" + key + '\'' +
                ", order=" + order +
                '}';
        }
    }

    static void foldInRange(final HasStepFolder itinerisStep, final Step<?, ?>  tinkerpopStep, final Traversal.Admin<?, ?> traversal, final List<HasContainer> hasContainers) {
        final Step<?, ?> nextStep = tinkerpopStep instanceof IdentityStep ? ItinerisTraversalUtil.getNextNonIdentityStep(tinkerpopStep): tinkerpopStep;
        if (nextStep instanceof RangeGlobalStep) {
            final RangeGlobalStep range = (RangeGlobalStep) nextStep;
            int low = 0;
            if (itinerisStep instanceof ItinerisStep) {
                low = QueryUtil.convertLimit(range.getLowRange());
                low = QueryUtil.mergeLowLimits(low, hasContainers == null ? itinerisStep.getLowLimit(): itinerisStep.getLocalLowLimit(traversal.getParent(), hasContainers));
            }
            int high = QueryUtil.convertLimit(range.getHighRange());
            high = QueryUtil.mergeHighLimits(high, hasContainers == null ? itinerisStep.getHighLimit(): itinerisStep.getLocalHighLimit(traversal.getParent(), hasContainers));
            if (hasContainers == null) {
                itinerisStep.setLimit(low, high);
            } else {
                itinerisStep.setLocalLimit(traversal.getParent(), hasContainers, low, high);
            }
            if (itinerisStep instanceof ItinerisStep || range.getLowRange() == 0) { //Range can be removed since there is ItinerisStep or no offset
                nextStep.getLabels().forEach(itinerisStep::addLabel);
                traversal.removeStep(nextStep);
            }
        }
    }

    /**
     * @param itinerisStep The step to test
     * @return True if there are 'has' steps following this step and no subsequent range limit step
     */
    static boolean foldableHasContainerNoLimit(FlatMapStep<?, ?> itinerisStep) {
        boolean foldableHasContainerNoLimit = false;
        Step<?, ?> currentStep = itinerisStep.getNextStep();
        while (true) {
            if (currentStep instanceof OrStep) {
                for (final Traversal.Admin<?, ?> child : ((OrStep<?>) currentStep).getLocalChildren()) {
                    if (!validFoldInHasContainer(child.getStartStep(), false)){
                        return false;
                    }
                }
                foldableHasContainerNoLimit =  true;
            } else if (currentStep instanceof HasContainerHolder) {
                if  (validFoldInHasContainer(currentStep, true)) {
                    foldableHasContainerNoLimit =  true;
                }
            } else if (currentStep instanceof RangeGlobalStep) {
                return false;
            } else if (!(currentStep instanceof IdentityStep) && !(currentStep instanceof NoOpBarrierStep)) {
                break;
            }
            currentStep = currentStep.getNextStep();
        }

        return foldableHasContainerNoLimit;
    }
}
