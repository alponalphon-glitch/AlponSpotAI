# NEXORA Master Control Brain

This update adds a single master control/report layer that reviews the existing analysis outputs and exposes a centralized subsystem registry in the decision screen.


## Integration
- `MasterControlBrain.kt`: centralized subsystem registry, evidence/status summary, fail-closed final action audit.
- `DecisionIntelligenceEngine.kt`: calls the master controller after the existing decision gates and attaches its report to `DecisionIntelligenceReport`.
- `MainActivity.kt`: displays the central report, subsystem statuses, and current blockers.

The controller does not create independent prediction claims, synthetic data, background monitoring, portfolio holdings, or live order execution. It may downgrade a proposed signal to `WAIT`; it does not bypass the existing decision gates.

Portfolio-wide exposure and exact execution costs remain explicitly unavailable until real inputs are connected.

## Six Quant Research diagnostics added
- Forecast Autopsy: explicitly reports that completed-horizon outcome matching is not connected.
- Regime-Specific Strategy Router: reports regime evidence as a proxy; per-regime validated strategy performance is not connected.
- Data Integrity Guardian: exposes the current integrity result and reasons.
- Prediction Tournament: records that controlled common-input model competition is not run.
- Optimal Observation Engine: states that waiting-value optimization is not calculated.
- Performance Attribution: states that paper outcome attribution inputs are missing.
These are honest central status/diagnostic entries, not claims of trained models or fully implemented autonomous features.


## Historical Memory & Intelligent Waiting added
- Added `HistoricalMemoryWaitingEngine`, a callable analysis-only engine that compares recent candle-return sequences against earlier supplied candle history.
- Reports the number of similar cases, historical directional frequency, and average 1-hour / 2-hour forward returns (horizons are derived from the supplied timeframe).
- Can return a WAIT / re-analyze-after-1h-or-2h recommendation when evidence is weak; it never places orders.
- The central controller now registers this capability. **The live app pipeline does not yet fetch/hand it 1–3 years of history automatically**, so full operational integration and validation remain future work. No accuracy is claimed.


## Unified Intelligence Workflow — latest additive update

The central controller now lists the proposed evidence-to-decision workflow and its diagnostic gates: verified data, historical memory, multi-analyst evidence, backtesting/walk-forward validation, probability calibration, outcome-based learning feedback, prediction comparison, adaptive risk/wait decision, and a final fail-closed decision. These are integration/status records. They do **not** claim that full historical fetching, outcome storage, backtest execution, calibration, or autonomous model promotion is already active end-to-end.

The intended decision path is: verified data → historical memory → analyst evidence → scenario/backtest validation → calibrated uncertainty → risk gates → final BUY/SELL/WAIT. Any unresolved data or risk blocker must keep the decision at WAIT.


## Seven Supreme Decision Brain diagnostics added (additive)
- Sequential Evidence Accumulator: sequenced evidence accumulation is described, but a persistent model is not wired in.
- Market Novelty Detector: historical novelty scoring is not yet validated or supplied.
- False Breakout & Trap Detector: current market-structure checks are not equivalent to a separately validated trap model.
- Opportunity Cost Brain: wait-vs-act expected value is not calculated yet.
- Horizon Convergence Engine: timeframe alignment/conflict is surfaced; a dedicated convergence score is not connected.
- Thesis Invalidation Engine: invalidation conditions are required conceptually, but a separate invalidation lifecycle is not wired into this controller.
- Strategy Resource Allocator: strategy ranking/allocation is not validated and no automatic promotion is claimed.
These seven items are additive central-controller diagnostics. They do not imply fully implemented, trained, or live-connected systems.


## Futures discipline and decision-stability safeguards added (2026-11-23)

The Master Control Brain now lists additional safeguards: anti-flip decision stability, Spot/Futures context separation, leverage and position-size inputs, liquidation/margin verification, funding/basis/open-interest context, volatility/correlation exposure review, mandatory trade-plan/exit discipline, and post-trade audit logging. These are central diagnostics and fail-closed requirements; they are **not evidence that exchange feeds, liquidation calculators, portfolio exposure, or execution controls are fully wired or validated**. Missing Futures risk inputs must not be inferred or treated as safe. The edit is additive; the previous MasterControlBrain source is preserved in a backup entry.


## Operational Guard Wiring Audit added
A central diagnostic record now explicitly distinguishes guard design from verified upstream enforcement. It checks the evidence supplied for anti-flip stability, Spot/Futures separation, Futures position sizing and liquidation, funding/basis/open-interest, volatility/correlation, and exit discipline. It warns that missing guard evidence is not proof of live enforcement and does not authorize autonomous Futures execution. Existing files were preserved; the MasterControlBrain source was backed up before editing.

## Eight Market Intelligence capabilities added (status/diagnostics)

The Master Control Brain now lists Market Regime Early Warning, Liquidity Map Intelligence, Event & News Impact Memory, Signal Contribution Intelligence, Cross-Market Dependency Brain, Execution Quality Intelligence, Confidence Decay Engine, and Strategy Stress Lab. These entries explicitly state the evidence/data requirements and validation status. This is a central-brain registry/diagnostic addition; it does not claim the underlying live feeds, validated models, or execution wiring are implemented. Existing files were preserved and a pre-edit backup of MasterControlBrain.kt was added.


## Autonomous Analysis Orchestrator added (2026-11-23)

A central-controller policy record describes command-free autonomous **analysis**: periodic and event-triggered market scans, fresh-data integrity checks, separate Spot/Futures evaluation, central safety gates, persisted snapshots/outcome review, deduplicated alerts, cooldowns and rate limits. Autonomous scope is limited to analysis, re-analysis, WAIT/NO-ENTRY decisions and reporting. It does not authorize automatic order placement, leverage changes, or live execution. The status explicitly says scheduler/persistence/feed wiring is still required; this registry entry alone does not activate a background service. All existing ZIP entries are retained, and the pre-edit MasterControlBrain source is backed up.
