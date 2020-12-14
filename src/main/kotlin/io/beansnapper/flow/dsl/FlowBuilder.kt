package io.beansnapper.flow.dsl

import io.beansnapper.annotations.NotThreadSafe
import io.beansnapper.flow.domain.*

const val defaultName = "unknown"
const val defaultTerminateName = "#terminate"

/**
 * intended for single-thread building of a flow
 */
@NotThreadSafe
class FlowBuilder() {
    var name: String = defaultName
    var defaultStart: StepBuilder? = null
    var defaultTerminal = StepBuilder(this, defaultTerminateName, {}).apply { terminalStep = true }
    val steps = mutableMapOf(defaultTerminal.name to defaultTerminal)
    val wires = mutableListOf<WireBuilder>()

    fun getStep(name: String) = steps[name] ?: throw BuilderException("no step named $name")

    class BuilderException(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : Exception()

    class StepBuilder(
        val flow: FlowBuilder,
        var name: String = defaultName,
        var action: ActionLambda,
        var startStep: Boolean = false,
        var terminalStep: Boolean = false,
    ) {
        private var theStep: Step? = null

        fun build(): Step {
            if (theStep == null) {
                theStep = Step(null, null, name, action, startStep, terminalStep)
            }
            return theStep!!
        }
    }

    class WireBuilder(
        val flow: FlowBuilder,
        var fromStep: StepBuilder? = null,
        var toStep: StepBuilder? = null,
    ) {
        init {
            flow.wires.add(this)
        }

        fun thenDo(stepName: String): WireBuilder {
            toStep = flow.getStep(stepName)
            return WireBuilder(flow, toStep)
        }

        fun andTerminate(stepName: String? = null) {
            toStep = if (stepName == null) {
                flow.defaultTerminal
            } else {
                val step = flow.getStep(stepName)
                if (!step.terminalStep) throw BuilderException("Step $stepName is not terminal")
                step
            }
        }

        internal fun build(): Wire {
            return Wire(
                null,
                null,
                RefId((fromStep ?: throw BuilderException("Wire is not fully defined")).build()),
                RefId((toStep ?: throw BuilderException("Wire is not fully defined")).build())
            )
        }

    }

    fun flow(builder: FlowBuilder.() -> Unit): FlowBuilder {
        builder.invoke(this)
        return this
    }

    fun step(name: String, action: ActionLambda): StepBuilder {
        if (steps.containsKey(name)) {
            throw BuilderException("duplicate step name $name")
        }

        val step = StepBuilder(this, name, action)
        steps[step.name] = step

        return step
    }

    fun start(name: String): WireBuilder {
        val step = getStep(name)
        defaultStart = step
        return WireBuilder(this, step)
    }

    fun build(): Flow {
        val theSteps = steps.values.map { it.build() }.map { RefId<Step>(it) }
        val theWires = wires.map { it.build() }.map { RefId<Wire>(it) }
        val theStart = RefId<Step>(
            defaultStart?.build() ?: throw BuilderException("The default start step wasn't defined")
        )

        return Flow(null, null, name, theSteps, theWires, theStart)
    }
}