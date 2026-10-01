"""
DAG Execution Engine
=====================
Executes steps in topological order with parallel support.
Contains all four step executors: query, compute, analyze, summarize.
"""

import logging
import json
import threading
import time
from datetime import date
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Optional

from system_b.config import Config
from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata
from system_b.models.exceptions import (
    DAGExecutionError, ComputationError, SystemAQueryError,
    SystemAConnectionError, SystemATimeoutError, LLMError,
)
from system_b.execution.reference_resolver import ContextStore, resolve_reference
from system_b.communication.system_a_client import SystemAClient
from system_b.computation.function_dispatcher import FunctionDispatcher
from system_b.utils.llm_helper import call_llm, call_llm_stream
from system_b.utils.step_result_notifier import AnalysisProgressReporter
from system_b.utils.step_result_callback_sender import StepResultCallbackSender
from system_b.utils.prompt_version_manager import get_current_prompt
from system_b.utils.tuning_support import current_prompt_overrides, set_prompt_overrides
from system_b.utils.llm_call_log import hash_content, archive_variable
from system_b.utils.prompt_template import render_template

logger = logging.getLogger(__name__)


class DAGEngine:
    """
    DAG-based execution engine that schedules and runs steps
    based on their dependency relationships.
    """

    def __init__(self, request_id: str, original_question: str, database_meta: dict, stream_name: str = ""):
        self.request_id = request_id
        self.original_question = original_question
        self.database_meta = database_meta
        self.context = ContextStore()
        self.system_a_client = SystemAClient()
        self.dispatcher = FunctionDispatcher(self.context)
        self.execution_log = []
        self.stream_name = stream_name
        self.progress_reporter = AnalysisProgressReporter(
            request_id=request_id,
            stream_name=stream_name,
        )
        self.result_callback_sender = StepResultCallbackSender(
            request_id=request_id,
            system_a_client=self.system_a_client,
        )

    def execute(self, steps: list[dict]) -> dict:
        """
        Execute all steps in topological order.

        Returns:
            {
                "answer": str,
                "data": StandardDataFrame or None,
                "execution_log": list
            }
        """
        logger.info(f"[DAG] Starting execution of {len(steps)} steps")

        # Phase 1: Build dependency graph
        graph, in_degree = self._build_dag(steps)
        step_map = {s["step_id"]: s for s in steps}

        # Phase 2: Topological sort -> execution levels
        levels = self._topological_sort(steps, in_degree, graph)
        logger.info(f"[DAG] Execution levels: {levels}")

        # Phase 3: Execute level by level
        for level_idx, level_steps in enumerate(levels):
            logger.info(f"[DAG] Executing level {level_idx}: {level_steps}")

            if len(level_steps) == 1:
                self._execute_step(step_map[level_steps[0]])
            else:
                # Parallel execution for independent steps
                with ThreadPoolExecutor(max_workers=Config.DAG_THREAD_POOL_SIZE) as executor:
                    futures = {}
                    for step_id in level_steps:
                        future = executor.submit(self._execute_step, step_map[step_id])
                        futures[future] = step_id

                    for future in as_completed(futures):
                        step_id = futures[future]
                        try:
                            future.result()
                        except Exception as e:
                            logger.error(f"[DAG] Step {step_id} failed in parallel: {e}")

        # Phase 4: Collect results
        answer = self._get_final_answer(steps)
        final_data = self._get_final_data(steps)

        return {
            "answer": answer,
            "data": final_data.to_dict() if final_data else None,
            "execution_log": self.execution_log,
        }

    def execute_streaming(
        self,
        step_generator,
        chat_session_id: str = "",
        chat_id: str = "",
        auth_header: str = "",
        tenant_id: str = "",
        skip_chat_clear: bool = False,
    ):
        """
        Streaming execution: consume steps from decompose_query_stream generator.
        Steps are executed sequentially in order. Before each step executes,
        its think message is pushed so the frontend sees what's about to happen.

        Returns:
            Same dict as execute(): {answer, data, execution_log}
        """
        logger.info("[DAG-Stream] Starting streaming execution")

        all_steps = []
        decomposition_meta = {"steps": [], "_system_prompt": "", "_user_prompt": "", "model_outputs": []}
        model_outputs = []
        last_attempt_error = ""

        # Parallel pipeline: as soon as the LLM yields a step, push its think
        # message and submit it for execution. Steps with no unfinished
        # dependencies run immediately in parallel; steps with dependencies
        # wait inside the worker thread.
        executor = ThreadPoolExecutor(max_workers=Config.DAG_THREAD_POOL_SIZE)
        futures = []

        # Steps whose results were pushed to System A during failed rounds.
        # Their history records are cleared via chat/clear once execution ends.
        clear_chat_ids = set()
        # 测试流量借生产 request_id 运行时必须跳过 chat/clear，
        # 否则测试失败会清掉 System A 上真实会话的步骤记录
        chat_clear_enabled = bool(chat_session_id and chat_id) and not skip_chat_clear
        decompose_failed = False

        # Summarize steps buffered until decomposition validation passes
        # (the done event); discarded on attempt_failed. See 0915 note below.
        buffered_summarize = []

        def _collect_pushed_step_ids():
            # Query/compute/analyze results reach System A synchronously inside
            # the step threads; summarize never pushes. Must run before
            # reset_steps wipes the statuses. Only adds (idempotent).
            clear_chat_ids.update(
                s["step_id"] for s in all_steps
                if s["step_type"] != "summarize"
                and self.context.get_step_status(s["step_id"]) in ("success", "failed")
            )

        # 调优草稿验证的 prompt_overrides 是线程级的，worker 线程需显式继承
        _prompt_overrides = current_prompt_overrides()

        def _run_step(step):
            if _prompt_overrides:
                set_prompt_overrides(_prompt_overrides)
            step_id = step["step_id"]
            deps = step.get("depends_on", [])
            if deps:
                self.context.wait_for_steps(deps)
            step_type = step["step_type"]
            if step_type == "summarize":
                self._execute_summarize_streaming(step)
            else:
                self._execute_step(step)

        try:
            for item in step_generator:
                item_type = item.get("type")
                if item_type == "attempt_started":
                    self.progress_reporter.attempt_started(item["attempt"], reason=last_attempt_error)
                    last_attempt_error = ""
                    continue
                if item_type == "raw_delta":
                    self.progress_reporter.notify_raw_stream(item["attempt"], item["delta"])
                    continue
                if item_type == "attempt_failed":
                    model_outputs.append({
                        "attempt": item["attempt"],
                        "status": "failed",
                        "error": item.get("error", ""),
                        "raw_output": item.get("full_text", ""),
                    })
                    self.progress_reporter.attempt_failed(item["attempt"], item.get("error", ""))
                    last_attempt_error = item.get("error", "")
                    decompose_failed = True

                    # The retry round reuses the same step ids; drop this
                    # round's log entries so the response only carries the
                    # round that ran to completion.
                    round_ids = {s["step_id"] for s in all_steps}
                    self.execution_log = [
                        e for e in self.execution_log if e.get("step_id") not in round_ids
                    ]

                    # Drain barrier: wait for leftover steps of the failed round
                    # so System A receives no more writes for it. Leftover steps
                    # are dead anyway (marked failed or skipped); waiting here
                    # means the chat/clear fired after the last retry races
                    # nothing.
                    for fut in futures:
                        if not fut.done():
                            try:
                                fut.result()
                            except Exception as e:
                                logger.warning(f"[DAG-Stream] Drain barrier: step raised: {e}")
                    futures.clear()

                    # Collect ids whose results already reached System A, then
                    # wipe per-step state: retry rounds reuse step ids, so
                    # stale completion events would otherwise make
                    # wait_for_steps pass instantly with the previous round's
                    # data.
                    _collect_pushed_step_ids()
                    self.context.reset_steps(sorted(round_ids))
                    # The buffered summarize of this round belongs to a plan
                    # that just failed validation; drop it.
                    buffered_summarize.clear()
                    continue
                if item_type == "done":
                    # Validation passed (cross-step checks run inside the
                    # decomposer before this event): submit the round's
                    # summarize now. register_step was deferred to here.
                    for s in buffered_summarize:
                        self.context.register_step(s["step_id"])
                        futures.append(executor.submit(_run_step, s))
                    buffered_summarize.clear()
                    model_outputs.append({
                        "attempt": item.get("attempt"),
                        "status": "success",
                        "error": "",
                        "raw_output": item.get("full_text", ""),
                    })
                    decomposition_meta["steps"] = item.get("steps", [])
                    decomposition_meta["_system_prompt"] = item.get("_system_prompt", "")
                    decomposition_meta["_user_prompt"] = item.get("_user_prompt", "")
                    decomposition_meta["model_outputs"] = model_outputs
                    break
                if item_type != "step":
                    continue

                step = item["step"]
                all_steps.append(step)
                step_id = step["step_id"]
                step_type = step["step_type"]

                # 0915 案例：summarize 与跨步校验赛跑——校验失败时总结已在
                # 执行并推送，失败轮的总结全部漏到 System A（前端拼接出
                # 重复且互相矛盾的总结）。summarize 是最后一步、跨步校验
                # 在流结束时才判，所以缓冲它直到 done（校验已通过）才执行；
                # attempt_failed 时直接丢弃，失败轮零执行零推送。
                if step_type == "summarize":
                    self.progress_reporter.decomposition_step(
                        step_id, f"{step_type}: {step.get('description', '')[:60]}"
                    )
                    buffered_summarize.append(step)
                    continue

                self.context.register_step(step_id)

                # Push think + title immediately
                self.progress_reporter.decomposition_step(
                    step_id, f"{step_type}: {step.get('description', '')[:60]}"
                )
                if step_type != "summarize":
                    self.progress_reporter.step_started(step_id, step_type, step.get("description", ""))

                # Submit for execution (waits for deps inside worker)
                futures.append(executor.submit(_run_step, step))

        finally:
            # Wait for all submitted steps to finish. On the final-failure
            # path (the generator raised right after attempt_failed) this is
            # also the drain barrier: leftover steps of the last round must
            # finish before chat/clear so System A receives no more writes
            # for the history being cleared.
            for future in as_completed(futures):
                try:
                    future.result()
                except Exception as e:
                    logger.error(f"[DAG-Stream] Step failed: {e}", exc_info=True)

            executor.shutdown(wait=True)

            # The last round's pushed step ids never went through the
            # attempt_failed collector (the generator raised before the
            # engine could react), so collect them here.
            if decompose_failed:
                _collect_pushed_step_ids()

            # Every failed round ended with a drain barrier and all remaining
            # steps finished above, so nothing is in flight anymore: safe to
            # clear the failed rounds' step records from System A history.
            # Non-fatal on failure (the API is idempotent; leftovers clear on
            # a later retry).
            if chat_clear_enabled and clear_chat_ids:
                logger.info(
                    f"[DAG-Stream] Clearing System A history for failed rounds: "
                    f"{sorted(clear_chat_ids)}"
                )
                self.system_a_client.clear_chat_history(
                    chat_session_id=chat_session_id,
                    chat_id=chat_id,
                    auth_header=auth_header or None,
                    tenant_id=tenant_id or None,
                )
            self.progress_reporter.think_done()

        # Phase 3: Collect results
        answer = self._get_final_answer(all_steps)
        final_data = self._get_final_data(all_steps)

        return {
            "answer": answer,
            "data": final_data.to_dict() if final_data else None,
            "execution_log": self.execution_log,
            "decomposition": decomposition_meta,
        }

    def _build_dag(self, steps: list[dict]) -> tuple:
        """Build DAG from step dependencies."""
        graph = defaultdict(list)
        in_degree = {s["step_id"]: 0 for s in steps}

        for step in steps:
            for dep in step.get("depends_on", []):
                graph[dep].append(step["step_id"])
                in_degree[step["step_id"]] += 1

        # Check for cycles
        visited = set()
        rec_stack = set()

        def has_cycle(node):
            visited.add(node)
            rec_stack.add(node)
            for neighbor in graph[node]:
                if neighbor not in visited:
                    if has_cycle(neighbor):
                        return True
                elif neighbor in rec_stack:
                    return True
            rec_stack.remove(node)
            return False

        for step in steps:
            if step["step_id"] not in visited:
                if has_cycle(step["step_id"]):
                    raise DAGExecutionError("Circular dependency detected in execution plan")

        return graph, in_degree

    def _topological_sort(self, steps, in_degree, graph) -> list[list[str]]:
        """Topological sort returning execution levels."""
        in_deg = dict(in_degree)
        levels = []
        all_ids = {s["step_id"] for s in steps}

        while True:
            level = [sid for sid in all_ids if in_deg.get(sid, 0) == 0 and sid in in_deg]
            if not level:
                break
            levels.append(sorted(level))
            for sid in level:
                del in_deg[sid]
                for neighbor in graph.get(sid, []):
                    if neighbor in in_deg:
                        in_deg[neighbor] -= 1

        return levels

    def _execute_step(self, step: dict):
        """Execute a single step based on its type."""
        step_id = step["step_id"]
        step_type = step["step_type"]
        # Check dependencies       # Check dependencies
        for dep in step.get("depends_on", []):
            status = self.context.get_step_status(dep)
            if status in ("failed", "skipped"):
                logger.warning(f"[DAG] Skipping {step_id}: dependency {dep} is {status}")
                self.context.set_step_status(step_id, "skipped", f"Dependency {dep} is {status}")
                self._log_step(step_id, step_type, "skipped", f"Dependency {dep} is {status}")
                self.progress_reporter.step_failed(step_id, step_type, f"依赖步骤 {dep} {status}")
                return

        # Check condition
        condition = step.get("condition")
        if condition:
            try:
                if not self._evaluate_condition(condition):
                    logger.info(f"[DAG] Skipping {step_id}: condition not met: {condition}")
                    self.context.set_step_status(step_id, "skipped", f"Condition not met: {condition}")
                    self._log_step(step_id, step_type, "skipped", f"Condition not met")
                    self.progress_reporter.step_failed(step_id, step_type, "条件不满足")
                    return
            except Exception as e:
                logger.warning(f"[DAG] Condition evaluation failed for {step_id}: {e}")

        self.context.set_step_status(step_id, "running")
        logger.info(f"[DAG] Executing {step_id} ({step_type}): {step.get('description', '')[:60]}")

        _step_t0 = time.time()
        try:
            if step_type == "query":
                self._execute_query(step)
            elif step_type == "compute":
                self._execute_compute(step)
            elif step_type == "analyze":
                self._execute_analyze(step)
            elif step_type == "summarize":
                self._execute_summarize_streaming(step)
            else:
                raise DAGExecutionError(f"Unknown step type: {step_type}")

            self.context.set_step_status(step_id, "success")
            _step_elapsed = time.time() - _step_t0
            self._log_step(step_id, step_type, "success")
            logger.info(f"[DAG] Step {step_id} ({step_type}) completed in {_step_elapsed:.2f}s")
            if step_type != "summarize":
                self.progress_reporter.step_succeeded(step_id, step_type)
        except Exception as e:
            _step_elapsed = time.time() - _step_t0
            logger.error(f"[DAG] Step {step_id} failed after {_step_elapsed:.2f}s: {e}", exc_info=True)
            self.context.set_step_status(step_id, "failed", str(e))
            self._log_step(step_id, step_type, "failed", str(e))
            self.progress_reporter.step_failed(step_id, step_type, str(e))
    # =====================================================================
    # Query Executor
    # =====================================================================
    def _execute_query(self, step: dict):
        """Execute a query step by calling System A.

        BUGFIX (issue #3): If filter values inside expected_columns reference
        upstream step outputs (e.g. "step_2.top_region.region_name"), resolve
        them to concrete values before forwarding to System A.
        """
        step_id = step["step_id"]
        params = step.get("params", {})

        query_description = params.get("query_description", "")
        time_range = params.get("time_range", {})
        entities = params.get("entities", [])
        expected_columns = params.get("expected_columns", [])
        expected_table = params.get("expected_table", "")

        # Resolve any references in expected_columns filter values.
        expected_columns = self._resolve_filter_value_refs(expected_columns)
        # Resolve any references in entities (sometimes used the same way).
        entities = self._resolve_entity_refs(entities)

        # Check for upstream entity injection
        context_info = {
            "original_question": self.original_question,
            "step_description": step.get("description", ""),
            "upstream_entities": [],
        }

        # Try to extract upstream entities from depends_on steps
        for dep in step.get("depends_on", []):
            if self.context.has_step(dep):
                dep_sdf = self.context.step_outputs[dep]
                dim_cols = dep_sdf.get_dimension_columns()
                if dim_cols:
                    dim_values = dep_sdf.get_column_values(dim_cols[0])
                    context_info["upstream_entities"].extend([str(v) for v in dim_values if v is not None])

        _query_t0 = time.time()
        sdf = self.system_a_client.query_data(
            request_id=self.request_id,
            step_id=step_id,
            query_description=step.get("description", ""),
            time_range=time_range,
            entities=entities,
            expected_columns=expected_columns,
            expected_table=expected_table,
            context=context_info,
            description=step.get("description", ""),
            database_meta=self.database_meta,
        )

        _query_elapsed = time.time() - _query_t0
        self.context.store(step_id, sdf)
        logger.info(f"[DAG] Query {step_id} returned {sdf.metadata.row_count} rows in {_query_elapsed:.2f}s")

    # ----- Helpers for resolving upstream references inside query params -----

    def _looks_like_step_ref(self, s) -> bool:
        """Check if a string value looks like a 'step_N...' reference."""
        import re as _re
        return isinstance(s, str) and bool(_re.match(r"^step_\d+(\.|$)", s.strip()))

    def _resolve_one_ref_value(self, ref_str: str):
        """Resolve a single 'step_X.op.col' or 'step_X.col' reference to
        a list of concrete values from the context.

        Returns:
            list of concrete values (always a list, possibly empty).
        """
        try:
            sdf = self.context.get(ref_str)
        except Exception as e:
            logger.warning(f"[DAG] Could not resolve filter value ref '{ref_str}': {e}")
            return [ref_str]  # leave the literal in place if resolution fails

        # Determine which column's values to extract.
        # Pattern: "step_X.op.col" -> col is the third part
        # Pattern: "step_X.col_or_op" -> if it's an operation key, fall back
        #   to the SDF's first measure column (or first column).
        parts = ref_str.strip().split(".")
        target_col = None
        if len(parts) >= 3:
            target_col = parts[-1]

        if target_col and sdf.has_column(target_col):
            vals = sdf.get_column_values(target_col)
        else:
            # Prefer the first dimension column (filters usually target dims),
            # otherwise fall back to the first column.
            dim_cols = sdf.get_dimension_columns()
            if dim_cols:
                vals = sdf.get_column_values(dim_cols[0])
            elif sdf.columns:
                vals = sdf.get_column_values(sdf.columns[0].name)
            else:
                vals = []

        # Deduplicate while preserving order, drop Nones.
        seen = set()
        out = []
        for v in vals:
            if v is None:
                continue
            key = str(v)
            if key in seen:
                continue
            seen.add(key)
            out.append(v)
        return out

    def _resolve_filter_value_refs(self, expected_columns: list) -> list:
        """Walk expected_columns and resolve any references inside filter values.

        Filter shape (from LLM):
            {"name": "region_name", "role": "dimension",
             "filter": [{"operator": "=", "values": ["step_2.top_region.region_name"]}]}
        """
        if not expected_columns:
            return expected_columns

        resolved_cols = []
        for ec in expected_columns:
            if not isinstance(ec, dict):
                resolved_cols.append(ec)
                continue
            new_ec = dict(ec)
            filters = new_ec.get("filter") or []
            if filters and isinstance(filters, list):
                new_filters = []
                for f in filters:
                    if not isinstance(f, dict):
                        new_filters.append(f)
                        continue
                    new_f = dict(f)
                    vals = new_f.get("values")
                    if isinstance(vals, list):
                        expanded = []
                        for v in vals:
                            if self._looks_like_step_ref(v):
                                resolved = self._resolve_one_ref_value(v)
                                logger.info(
                                    f"[DAG] Resolved filter value ref '{v}' -> {resolved}"
                                )
                                expanded.extend(resolved)
                            else:
                                expanded.append(v)
                        new_f["values"] = expanded
                    # Also support single 'value' field.
                    if "value" in new_f and self._looks_like_step_ref(new_f["value"]):
                        resolved = self._resolve_one_ref_value(new_f["value"])
                        logger.info(
                            f"[DAG] Resolved filter value ref '{new_f['value']}' -> {resolved}"
                        )
                        # Promote to 'values' if multiple, otherwise keep single.
                        if len(resolved) == 1:
                            new_f["value"] = resolved[0]
                        else:
                            new_f.pop("value", None)
                            new_f["values"] = resolved
                    new_filters.append(new_f)
                new_ec["filter"] = new_filters
            resolved_cols.append(new_ec)
        return resolved_cols

    def _resolve_entity_refs(self, entities: list) -> list:
        """Resolve any step references inside the entities list."""
        if not entities:
            return entities
        out = []
        for e in entities:
            if self._looks_like_step_ref(e):
                resolved = self._resolve_one_ref_value(e)
                out.extend(resolved)
            else:
                out.append(e)
        return out

    # =====================================================================
    # Compute Executor
    # =====================================================================
    def _execute_compute(self, step: dict):
        """Execute a compute step with sequential operations.

        BUGFIX (issues #2/#4/#8): When a compute step has multiple sibling
        operations that all produce scalar results (e.g. several aggregate
        operations computing different measures from the same input),
        merge them into a single multi-measure scalar StandardDataFrame as
        the step's final output rather than dropping all but the last one.
        """
        step_id = step["step_id"]
        params = step.get("params", {})
        operations = params.get("operations", [])
        _compute_t0 = time.time()

        if not operations:
            logger.warning(f"[DAG] No operations in compute step {step_id}")
            self.context.store(step_id, StandardDataFrame.empty(source_step=step_id))
            return

        # Track all per-operation outputs in execution order (for the
        # multi-scalar merge fallback below).
        per_op_results = []  # list of (output_name, StandardDataFrame)
        last_result = None
        produced_op_names = []
        for i, operation in enumerate(operations):
            # Mirror the same priority logic as the dispatcher so we record
            # the actual output name even when LLM puts it inside `inputs`.
            inputs_block = operation.get("inputs", {}) or {}
            op_output_name = (
                operation.get("output")
                or (inputs_block.get("output") if isinstance(inputs_block, dict) else None)
                or f"op_{i}"
            )
            try:
                op_result = self.dispatcher.execute_operation(
                    step_id, operation, depends_on=step.get("depends_on", []))
                last_result = op_result
                produced_op_names.append(op_output_name)
                per_op_results.append((op_output_name, op_result))
            except ComputationError as e:
                logger.error(f"[DAG] Operation {i} in {step_id} failed: {e}")
                # Mark null result for this operation
                null_result = StandardDataFrame.empty(source_step=step_id, description=f"Failed: {e}")
                self.context.store(step_id, null_result, operation_name=op_output_name)
                last_result = null_result
                per_op_results.append((op_output_name, null_result))

        # Decide what the step-level output should be.
        # Heuristic 0 (0723 问题42): SINGLE-CHAIN pipeline detection.
        # When the operations form one linear pipeline (each intermediate
        # output is consumed by a later operation and only the LAST output
        # is unconsumed, e.g. filter -> rank -> filter rank<=10), the last
        # operation's result IS the intended step result. The wide-table
        # merge below would re-join the final selection with the full
        # intermediate tables and blow the row count back up (问题42: the
        # per-branch top-10-days selection was merged back into 413 rows).
        op_names_in_order = [name for name, _ in per_op_results]
        internal_consumed = set()

        def _collect_refs(v):
            if isinstance(v, str):
                for n in op_names_in_order:
                    if v == n or v == f"{step_id}.{n}" or v.startswith(f"{step_id}.{n}."):
                        internal_consumed.add(n)
            elif isinstance(v, list):
                for x in v:
                    _collect_refs(x)
            elif isinstance(v, dict):
                for x in v.values():
                    _collect_refs(x)

        for _op in operations:
            _collect_refs(_op.get("inputs", {}) or {})
        unconsumed = [n for n in op_names_in_order if n not in internal_consumed]
        # Guard: a genuine refinement pipeline never INCREASES the row count
        # (filter/rank/top_n shrink or preserve rows). If the last op blew
        # rows up (e.g. a degenerate cross-join), fall through to the merge
        # heuristics below which can repair it.
        _max_intermediate_rows = max(
            (len(s.rows) for _, s in per_op_results[:-1]
             if isinstance(s, StandardDataFrame)), default=0)
        # Also require the final result to be MULTI-ROW: single-row chain
        # tails (e.g. top_n(1) -> bottom_n(1) -> subtract gap) are KPI
        # patterns where the intermediate values matter to the user; those
        # are handled by the single-row merge heuristics below.
        if (len(operations) >= 2
                and len(unconsumed) == 1
                and unconsumed[0] == op_names_in_order[-1]
                and isinstance(last_result, StandardDataFrame)
                and len(last_result.rows) > 1
                and len(last_result.rows) <= max(_max_intermediate_rows, 1)):
            self.context.store(step_id, last_result)
            self.result_callback_sender.send_step_result(step, last_result)
            _elapsed = time.time() - _compute_t0
            logger.info(
                f"[DAG] Compute step {step_id}: single-chain pipeline detected "
                f"(elapsed={_elapsed:.2f}s, "
                f"({' -> '.join(op_names_in_order)}); step output = last operation "
                f"'{op_names_in_order[-1]}' ({last_result.metadata.row_count} rows)"
            )
            return

        # Heuristic 1 (issues #2/#4/#8): if the compute step had >=2 sibling
        # operations that each produced a non-empty scalar result, merge them
        # into one multi-measure scalar dataframe so the summarize step sees
        # ALL of them (not just the last one).
        scalar_outputs = [
            (name, sdf) for (name, sdf) in per_op_results
            if isinstance(sdf, StandardDataFrame)
            and sdf.metadata.is_scalar
            and sdf.rows
            and sdf.rows[0]
        ]

        if len(scalar_outputs) >= 2 and len(scalar_outputs) == len(per_op_results):
            merged_row = {}
            merged_cols = []
            seen_col_names = set()
            for name, sdf in scalar_outputs:
                # The scalar SDF has a single measure column; its name is
                # already the operation's output name.
                for c in sdf.columns:
                    if c.name in seen_col_names:
                        continue
                    seen_col_names.add(c.name)
                    merged_cols.append(c.copy())
                if sdf.rows:
                    for k, v in sdf.rows[0].items():
                        if k not in merged_row:
                            merged_row[k] = v

            merged_meta = Metadata(
                source_step=step_id,
                source_operation=None,
                description=f"Merged scalars from {len(scalar_outputs)} operations: "
                            + ", ".join(name for name, _ in scalar_outputs),
                row_count=1,
                is_scalar=True,
                lineage=[step_id] + [f"{step_id}.{name}" for name, _ in scalar_outputs],
            )
            merged_sdf = StandardDataFrame(
                metadata=merged_meta,
                columns=merged_cols,
                rows=[merged_row],
            )
            self.context.store(step_id, merged_sdf)
            self.result_callback_sender.send_step_result(step, merged_sdf)
            logger.info(
                f"[DAG] Compute step {step_id}: merged {len(scalar_outputs)} scalar outputs "
                f"({list(merged_row.keys())}) into single step output"
            )
            return

        # Heuristic 1.5 (新问题5 fix, 7-18轮 + 0723 问题3/8/10/75/111 重构):
        # merge SINGLE-ROW sibling outputs so the step result carries ALL the
        # computed KPIs instead of just the last operation.
        #
        # 0723 改进点：
        #   a) 全部 single-row 且列集合完全相同（典型 top_n(1)+bottom_n(1)）
        #      → 纵向 STACK 成多行（此前横向合并只保留第一个 op 的
        #      year_month/monthly_supply 值，"最低月份"被吞掉 — 问题3/10/75）。
        #   b) 横向合并时列名冲突且值不同 → 冲突列改名 f"{opname}_{col}"
        #      而不是丢弃后到者的值。
        #   c) 混合场景（既有多行中间表又有 >=2 个单行 KPI，且最后一个 op 是
        #      单行）→ 用单行 KPI 合并结果作为 step 输出（此前要么只剩最后
        #      一个 op，要么宽表合并把所有标量 KPI 丢掉 — 问题111/61/102/107）。
        single_row_outputs = [
            (name, sdf) for (name, sdf) in per_op_results
            if isinstance(sdf, StandardDataFrame) and len(sdf.rows) == 1 and sdf.rows[0]
        ]

        def _merge_single_rows(outputs):
            """Merge a list of (name, single-row SDF).

            Returns a merged SDF: stacked rows when all outputs share one
            column set, else one wide row with collision-renaming.
            """
            col_sets = {frozenset(c.name for c in sdf.columns) for _, sdf in outputs}
            if len(col_sets) == 1 and len(outputs) >= 2:
                # identical column sets -> stack vertically (keep op order)
                rows = []
                seen_rows = set()
                for _, sdf in outputs:
                    key = tuple(sorted((k, str(v)) for k, v in sdf.rows[0].items()))
                    if key in seen_rows:
                        continue
                    seen_rows.add(key)
                    rows.append(dict(sdf.rows[0]))
                if len(rows) > 1:
                    cols = [c.copy() for c in outputs[0][1].columns]
                    meta = Metadata(
                        source_step=step_id,
                        source_operation=None,
                        description=(
                            f"Stacked single-row outputs from {len(outputs)} operations: "
                            + ", ".join(n for n, _ in outputs)),
                        row_count=len(rows),
                        is_scalar=False,
                        lineage=[step_id] + [f"{step_id}.{n}" for n, _ in outputs],
                    )
                    return StandardDataFrame(metadata=meta, columns=cols, rows=rows)
            # wide merge with collision renaming
            merged_row = {}
            merged_cols = []
            seen_col_names = set()
            for name, sdf in outputs:
                for c in sdf.columns:
                    v = sdf.rows[0].get(c.name)
                    if c.name not in seen_col_names:
                        seen_col_names.add(c.name)
                        merged_cols.append(c.copy())
                        merged_row[c.name] = v
                        continue
                    existing = merged_row.get(c.name)
                    if existing is None and v is not None:
                        merged_row[c.name] = v
                    elif v is not None and v != existing:
                        # collision with a DIFFERENT value -> keep both by
                        # renaming the later one (问题3: bottom_n 的月份值
                        # 之前被直接丢弃)
                        alt = f"{name}_{c.name}"
                        if alt not in seen_col_names:
                            seen_col_names.add(alt)
                            cc = c.copy()
                            cc.name = alt
                            merged_cols.append(cc)
                            merged_row[alt] = v
            meta = Metadata(
                source_step=step_id,
                source_operation=None,
                description=(
                    f"Merged single-row outputs from {len(outputs)} operations: "
                    + ", ".join(n for n, _ in outputs)),
                row_count=1,
                is_scalar=all(s.metadata.is_scalar for _, s in outputs),
                lineage=[step_id] + [f"{step_id}.{n}" for n, _ in outputs],
            )
            return StandardDataFrame(metadata=meta, columns=merged_cols, rows=[merged_row])

        mixed_kpi_case = (
            len(single_row_outputs) >= 2
            and len(single_row_outputs) < len(per_op_results)
            and last_result is not None
            and isinstance(last_result, StandardDataFrame)
            and len(last_result.rows) == 1
        )
        if (len(single_row_outputs) >= 2
                and (len(single_row_outputs) == len(per_op_results) or mixed_kpi_case)):
            # 0723 问题107 refinement: in the MIXED case, when the single-row
            # KPIs carry NEW columns (e.g. max_supply / min_supply / gap) and
            # the step also produced multi-row tables (e.g. per-district
            # totals with the top-5 / bottom-5 names), outputting ONLY the
            # merged KPIs would drop the entity names the user asked for.
            # Broadcast the KPI values as constant columns onto the wide
            # multi-row base instead, keeping BOTH the names and the KPIs.
            merged_sdf = None
            multi_row_names = [
                n for (n, s) in per_op_results
                if isinstance(s, StandardDataFrame) and len(s.rows) > 1
            ]
            if mixed_kpi_case and multi_row_names:
                base = None
                if len(multi_row_names) >= 2:
                    base = self._merge_operation_outputs(step_id, multi_row_names)
                if base is None:
                    # sole (or unmergeable) multi-row output -> use the last one
                    for n, s in reversed(per_op_results):
                        if isinstance(s, StandardDataFrame) and len(s.rows) > 1:
                            base = s
                            break
                if base is not None:
                    base_col_names = {c.name for c in base.columns}
                    kpi_new_cols = any(
                        c.name not in base_col_names
                        for _, s in single_row_outputs
                        for c in s.columns if c.role != "dimension"
                    )
                    if kpi_new_cols:
                        new_cols = [c.copy() for c in base.columns]
                        new_rows = [dict(r) for r in base.rows]
                        seen = set(base_col_names)
                        for name, s in single_row_outputs:
                            for c in s.columns:
                                if c.role == "dimension":
                                    continue
                                col_name = c.name
                                if col_name in seen:
                                    continue
                                seen.add(col_name)
                                cc = c.copy()
                                cc.name = col_name
                                new_cols.append(cc)
                                v = s.rows[0].get(c.name)
                                for r in new_rows:
                                    r[col_name] = v
                        meta = Metadata(
                            source_step=step_id,
                            description=(
                                f"Wide table of {len(multi_row_names)} multi-row outputs "
                                f"with {len(single_row_outputs)} KPI columns broadcast"),
                            row_count=len(new_rows),
                            is_scalar=False,
                            lineage=[step_id] + [f"{step_id}.{n}" for n, _ in per_op_results],
                        )
                        merged_sdf = StandardDataFrame(
                            metadata=meta, columns=new_cols, rows=new_rows)
            if merged_sdf is None:
                merged_sdf = _merge_single_rows(single_row_outputs)
            self.context.store(step_id, merged_sdf)
            self.result_callback_sender.send_step_result(step, merged_sdf)
            _elapsed = time.time() - _compute_t0
            logger.info(
                f"[DAG] Compute step {step_id}: merged {len(single_row_outputs)} single-row "
                f"operation outputs into step output "
                f"(rows={merged_sdf.metadata.row_count}, mixed={mixed_kpi_case}, elapsed={_elapsed:.2f}s)"
            )
            return

        # Heuristic 2: try to produce a wide-table merged view of all
        # non-scalar operation outputs so downstream references like
        # `step_N.<col>` can still be resolved when multiple operations
        # exist side-by-side (e.g. several group_by aggregates).
        merged = self._merge_operation_outputs(step_id, produced_op_names)
        # 0723 问题63 fix: the result callback previously ALWAYS sent
        # last_result even when the stored step output was the merged wide
        # table — the user-facing step display then showed only the LAST
        # operation (e.g. consumption_below_avg) while the other computed
        # ratios silently existed only in the context store. Send whatever
        # is actually stored as the step output.
        if merged is not None and merged.metadata.row_count > 0:
            self.context.store(step_id, merged)
            final_result = merged
        elif last_result is not None:
            # Fallback: last operation's result (preserves backward compat).
            self.context.store(step_id, last_result)
            final_result = last_result
        else:
            final_result = last_result
        self.result_callback_sender.send_step_result(step, final_result)
        _elapsed = time.time() - _compute_t0
        logger.info(f"[DAG] Compute step {step_id} completed (elapsed={_elapsed:.2f}s)")

    def _merge_operation_outputs(
        self, step_id: str, op_names: list
    ) -> Optional[StandardDataFrame]:
        """
        Merge multiple operation outputs of the current step into a single
        wide StandardDataFrame, joined on their common dimension columns.

        Returns None if merging is not meaningful (e.g. no common dims, only
        one op, all scalars, etc.).
        """
        if not op_names or len(op_names) < 2:
            return None

        sdfs = []
        for name in op_names:
            key = f"{step_id}.{name}"
            sdf = self.context.operation_outputs.get(key)
            if sdf is None or sdf.metadata.row_count == 0:
                continue
            if sdf.metadata.is_scalar:
                # Skip scalars; they'd force a cross-join and usually aren't
                # part of the grouped wide table the user expects.
                continue
            sdfs.append(sdf)

        if len(sdfs) < 2:
            return None

        # Find common dimension columns across all SDFs
        dim_sets = [set(s.get_dimension_columns()) for s in sdfs]
        common_dims = set.intersection(*dim_sets) if dim_sets else set()
        if not common_dims:
            # No shared dimensions to join on. If every output is a single-row
            # table (e.g. several per-industry growth metrics each landing on
            # their own measure column), cross-merge them into ONE wide row so
            # downstream summarize/reference steps see ALL sibling metrics
            # instead of just the last one (fixes issues #3/#6 — dimensionless
            # sibling metrics being dropped).
            if all(len(s.rows) == 1 for s in sdfs):
                merged_row = {}
                merged_cols = []
                seen = set()
                for s in sdfs:
                    for c in s.columns:
                        if c.name in seen:
                            continue
                        seen.add(c.name)
                        merged_cols.append(c.copy())
                    for k, v in s.rows[0].items():
                        if k not in merged_row:
                            merged_row[k] = v
                merged_meta = Metadata(
                    source_step=step_id,
                    description=f"Cross-merged {len(sdfs)} single-row operation outputs",
                    row_count=1,
                    lineage=[step_id],
                )
                return StandardDataFrame(
                    metadata=merged_meta,
                    columns=merged_cols,
                    rows=[merged_row],
                )
            return None
        # Preserve dim order from the first SDF
        first_dims = [d for d in sdfs[0].get_dimension_columns() if d in common_dims]

        # Start with first SDF; merge in remaining ones row-by-row on common dims
        base_rows = [dict(r) for r in sdfs[0].rows]
        base_cols = {c.name: c.copy() for c in sdfs[0].columns}

        for other in sdfs[1:]:
            other_index = {}
            for r in other.rows:
                k = tuple(r.get(d) for d in first_dims)
                other_index[k] = r
            # Merge into base rows
            new_base_rows = []
            for r in base_rows:
                k = tuple(r.get(d) for d in first_dims)
                other_row = other_index.get(k)
                merged_row = dict(r)
                if other_row:
                    for col_name, val in other_row.items():
                        if col_name in first_dims:
                            continue
                        # Avoid clobbering existing keys unless value is None
                        if col_name not in merged_row or merged_row.get(col_name) is None:
                            merged_row[col_name] = val
                new_base_rows.append(merged_row)
            base_rows = new_base_rows
            # Extend column defs
            for c in other.columns:
                if c.name in first_dims:
                    continue
                if c.name not in base_cols:
                    base_cols[c.name] = c.copy()

        # Build final SDF
        # Dimension columns come first
        ordered_cols = []
        seen = set()
        for d in first_dims:
            if d in base_cols and d not in seen:
                ordered_cols.append(base_cols[d])
                seen.add(d)
        for name, col in base_cols.items():
            if name not in seen:
                ordered_cols.append(col)
                seen.add(name)

        merged_meta = Metadata(
            source_step=step_id,
            description=f"Merged wide table of {len(sdfs)} operation outputs",
            row_count=len(base_rows),
            lineage=[step_id],
        )
        return StandardDataFrame(
            metadata=merged_meta,
            columns=ordered_cols,
            rows=base_rows,
        )

    # =====================================================================
    # Analyze Executor
    # =====================================================================
    def _execute_analyze(self, step: dict):
        """Execute an analyze step: supplementary queries + LLM reasoning."""
        step_id = step["step_id"]
        params = step.get("params", {})
        _analyze_t0 = time.time()

        analysis_target = params.get("analysis_target", "")
        analysis_dimensions = params.get("analysis_dimensions", [])
        supplementary_queries = params.get("supplementary_queries", [])

        # Phase 1: Supplementary queries
        supplementary_results = []
        for i, sq in enumerate(supplementary_queries):
            try:
                # Resolve upstream entities
                upstream_entities = []
                related_step = sq.get("related_to_step", "")
                if related_step:
                    try:
                        related_sdf = self.context.get(related_step)
                        dim_cols = related_sdf.get_dimension_columns()
                        if dim_cols:
                            upstream_entities = [str(v) for v in related_sdf.get_column_values(dim_cols[0]) if v is not None]
                    except Exception as e:
                        logger.warning(f"[DAG] Cannot resolve related_to_step {related_step}: {e}")

                sq_result = self.system_a_client.query_data(
                    request_id=self.request_id,
                    step_id=f"{step_id}_sq_{i}",
                    query_description=sq.get("description", ""),
                    time_range=sq.get("time_range", {}),
                    entities=[],
                    expected_columns=sq.get("expected_columns", []),
                    context={
                        "original_question": self.original_question,
                        "step_description": step.get("description", ""),
                        "upstream_entities": upstream_entities,
                    },
                    description=sq.get("query_description", ""),
                    database_meta=self.database_meta,
                )

                self.context.store(step_id, sq_result, operation_name=f"supplementary_{i}")
                supplementary_results.append({"index": i, "status": "success", "data": sq_result})

            except Exception as e:
                logger.warning(f"[DAG] Supplementary query {i} in {step_id} failed: {e}")
                supplementary_results.append({"index": i, "status": "failed", "error": str(e)})

        # Phase 2: LLM reasoning
        # Gather context data from depends_on
        context_data_text = []
        for dep in step.get("depends_on", []):
            if self.context.has_step(dep):
                dep_sdf = self.context.step_outputs[dep]
                context_data_text.append(f"--- {dep} ---\n{dep_sdf.to_display_text(max_rows=30)}")

        # Supplementary data
        supp_text = []
        for sr in supplementary_results:
            if sr["status"] == "success":
                supp_text.append(f"--- Supplementary query {sr['index']} ---\n{sr['data'].to_display_text(max_rows=30)}")
            else:
                supp_text.append(f"--- Supplementary query {sr['index']} --- DATA MISSING: {sr.get('error', 'unknown')}")

        analysis_prompt = f"""你是一个数据分析专家，需要根据以下数据分析异常的可能原因。

分析目标：{analysis_target}

分析维度：{', '.join(analysis_dimensions)}

异常数据：
{chr(10).join(context_data_text)}

关联因素数据：
{chr(10).join(supp_text)}

请综合各维度数据，给出每个异常实体的可能原因分析，每个原因需有数据支撑。输出格式为结构化文字分析。"""

        try:
            analysis_parts = []
            for delta in call_llm_stream(
                system_prompt="你是一个数据分析专家，擅长根据数据进行原因分析。",
                user_prompt=analysis_prompt,
                temperature=0.3,
                request_id="", source="step_analysis",
            ):
                analysis_parts.append(delta)
                self.progress_reporter.notify_analysis_stream(step_id, delta)
            self.progress_reporter.analysis_done(step_id)
            analysis_result = "".join(analysis_parts)
        except LLMError as e:
            logger.error(f"[DAG] LLM analysis failed for {step_id}: {e}")
            self.progress_reporter.step_failed(step_id, "analyze", str(e))
            analysis_result = f"原因分析未能完成: {e}"

        # Package as StandardDataFrame
        # 错误问题汇总 问题4 fix (非常严重): 之前的实现把同一份 LLM 分析文本
        # 广播到依赖步骤第一维度列的每个取值上 —— 依赖有 1000 行日期时就会产生
        # 1000 行完全相同的 analysis_result。分析本身是对整体的一段综合文字，
        # 因此固定输出 **单行**（entity=分析目标），不再按实体广播。
        entity_label = analysis_target or "overall"
        rows = [{"entity": str(entity_label), "analysis_result": analysis_result}]

        result_sdf = StandardDataFrame(
            metadata=Metadata(
                source_step=step_id,
                description=f"Analysis: {analysis_target}",
                row_count=len(rows),
                lineage=[step_id],
            ),
            columns=[
                ColumnDef(name="entity", role="dimension", data_type="string", description="Analysis target entity"),
                ColumnDef(name="analysis_result", role="measure", data_type="string", description="Analysis result text"),
            ],
            rows=rows,
        )

        self.context.store(step_id, result_sdf)
        self.result_callback_sender.send_step_result(step, result_sdf)

    # =====================================================================
    # Summarize Executor
    # =====================================================================
    def _build_summary_prompt(self, step: dict) -> tuple:
        """Build (system_prompt, user_prompt) for a summarize step."""
        step_id = step["step_id"]
        params = step.get("params", {})
        response_format = params.get("response_format", "text")
        emphasis = params.get("emphasis", "both")

        # Collect upstream data from direct dependencies
        data_sections = []
        included_steps = set()
        # 0806 fix: 总结提示词附带"列含义说明"字典。此前只把英文列名给
        # 总结模型，模型靠猜翻译（copy_num→"数据复制数量"，实际是"抄见数"；
        # sus_code_num→"符合规范的代码数量"，实际是"暂停表码数"），导致总结
        # 内容与用户问题不相关。这里汇总所有进入总结的列的 名称/中文描述/
        # 单位/角色，作为独立的"指标与维度说明"段落喷入提示词。
        column_glossary: dict[str, dict] = {}

        def _collect_glossary(sdf) -> None:
            for _c in sdf.columns:
                if _c.name in column_glossary:
                    # 保留信息更完整的一条（有 description 优先）
                    if column_glossary[_c.name].get("desc") or not (_c.description or ""):
                        continue
                column_glossary[_c.name] = {
                    "desc": _c.description or "",
                    "unit": _c.unit or "",
                    "role": _c.role or "",
                }

        for dep in step.get("depends_on", []):
            if self.context.has_step(dep):
                dep_sdf = self.context.step_outputs[dep]
                dep_status = self.context.get_step_status(dep)

                if dep_status != "success":
                    data_sections.append(f"--- {dep} (status: {dep_status}) ---\nData unavailable")
                    continue

                display_text = self._truncate_for_summary(dep_sdf, dep)
                data_sections.append(f"--- {dep} ---\n{display_text}")
                included_steps.add(dep)
                _collect_glossary(dep_sdf)

        for other_step in self.context.step_outputs:
            if other_step not in included_steps and other_step != step_id:
                other_sdf = self.context.step_outputs[other_step]
                other_status = self.context.get_step_status(other_step)
                if other_status == "success" and other_sdf.metadata.row_count > 0:
                    display_text = self._truncate_for_summary(other_sdf, other_step)
                    data_sections.append(f"--- {other_step} ---\n{display_text}")
                    included_steps.add(other_step)
                    _collect_glossary(other_sdf)

        _MAX_SMALL_OP_ROWS = 30
        _MAX_SMALL_OP_SECTIONS = 8
        small_op_count = 0
        for op_key, op_sdf in self.context.operation_outputs.items():
            if small_op_count >= _MAX_SMALL_OP_SECTIONS:
                break
            src_step = op_key.split(".", 1)[0]
            if src_step == step_id:
                continue
            if self.context.get_step_status(src_step) != "success":
                continue
            if not (0 < op_sdf.metadata.row_count <= _MAX_SMALL_OP_ROWS):
                continue
            if op_sdf is self.context.step_outputs.get(src_step):
                continue
            data_sections.append(
                f"--- {op_key} (中间计算结果, 完整) ---\n{op_sdf.to_display_text()}")
            small_op_count += 1
            _collect_glossary(op_sdf)

        # 构造"指标与维度说明"段落（只列有实质信息的列）
        glossary_lines = []
        for _name, _info in column_glossary.items():
            bits = []
            if _info["desc"]:
                bits.append(_info["desc"])
            if _info["unit"]:
                bits.append(f"单位：{_info['unit']}")
            if _info["role"]:
                bits.append("维度" if _info["role"] == "dimension" else "指标")
            if bits:
                glossary_lines.append(f"- {_name}：{'，'.join(bits)}")
        glossary_section = ""
        if glossary_lines:
            glossary_section = (
                "\n指标与维度说明（列名 → 中文含义，回答时必须使用这里的中文名称称呼指标，"
                "严禁自行猜测/翻译英文列名的含义）：\n" + "\n".join(glossary_lines) + "\n"
            )

        format_instruction = {
            "text": "请以自然语言段落形式回答。",
            "table": "请以Markdown表格形式呈现关键数据。",
            "chart_suggestion": "请在回答末尾追加可视化建议（适合用什么图表展示）。",
        }.get(response_format, "请以自然语言段落形式回答。")

        emphasis_instruction = {
            "dimension": "回答应突出'是哪个/哪些'的维度信息。",
            "measure": "回答应突出具体的数值和单位。",
            "both": "回答需同时包含维度信息和具体数值。",
        }.get(emphasis, "回答需同时包含维度信息和具体数值。")

        # Defaults (fallback when prompt-summary group doesn't exist)
        _default_system = "你是一个数据分析报告撰写者，需要根据数据分析结果回答用户问题。请用中文回答。"
        _default_user_template = """用户原始问题：{original_question}
当前日期：{current_date}
{glossary_section}
数据分析结果：
{data_sections}

{format_instruction}
{emphasis_instruction}

回答要求：
1. 必须准确引用数据中的具体数值
2. 必须附带正确的单位
3. 如果数据中有缺失或步骤执行失败，需如实说明
4. 不要编造数据中不存在的信息
5. 语言简洁明了，直接回答问题
6. 当前日期：{current_date}
7. 提及指标/维度时必须使用"指标与维度说明"中的中文名称，严禁根据英文列名自行猜测含义"""

        raw, summary_meta = get_current_prompt("prompt-summary")
        if raw and "=====USER_TEMPLATE=====" in raw:
            parts = raw.split("=====USER_TEMPLATE=====", 1)
            system_prompt = parts[0].strip()
            user_template = parts[1].strip()
        else:
            system_prompt = _default_system
            user_template = _default_user_template

        user_prompt = render_template(user_template,
            original_question=self.original_question,
            current_date=date.today().isoformat(),
            glossary_section=glossary_section,
            data_sections=chr(10).join(data_sections),
            format_instruction=format_instruction,
            emphasis_instruction=emphasis_instruction,
        )
        # Compute hash for glossary_section (the large database_meta part)
        if glossary_section:
            gloss_hash = hash_content(glossary_section)
            archive_variable("glossary", gloss_hash, glossary_section)
        else:
            gloss_hash = ""

        summary_prompt_ref = summary_meta if summary_meta else {}
        summary_usr_vars = {
            "original_question": self.original_question,
            "gloss_hash": gloss_hash,
            "gloss_len": len(glossary_section),
        }

        return system_prompt, user_prompt, summary_prompt_ref, summary_usr_vars

    def _store_summary_result(self, step: dict, answer: str) -> StandardDataFrame:
        """Store summary answer as StandardDataFrame and push to callbacks."""
        step_id = step["step_id"]
        result_sdf = StandardDataFrame(
            metadata=Metadata(
                source_step=step_id,
                description="Summary result",
                row_count=1,
                is_scalar=True,
                lineage=[step_id],
            ),
            columns=[ColumnDef(name="answer", role="measure", data_type="string", description="Natural language answer")],
            rows=[{"answer": answer}],
        )
        self.context.store(step_id, result_sdf)
        self.result_callback_sender.send_step_result(step, result_sdf)
        return result_sdf

    def _execute_summarize_streaming(self, step: dict):
        """Execute a summarize step with streaming LLM.
        每收到一个 token 就立即通过 Redis Stream 推送到前端。
        """
        step_id = step["step_id"]
        _summary_t0 = time.time()
        self.progress_reporter.step_started(step_id, "summarize", step.get("description", ""))
        system_prompt, user_prompt, summary_prompt_ref, summary_usr_vars = self._build_summary_prompt(step)
        answer_parts = []

        try:
            for delta in call_llm_stream(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                temperature=0.3,
                request_id="", source="summary",
                sys_prompt_ref=summary_prompt_ref,
                usr_template_ref=summary_prompt_ref,
                usr_vars=summary_usr_vars,
            ):
                answer_parts.append(delta)
                self.progress_reporter.notify_summary_stream(step_id, delta)
            self.progress_reporter.summary_done(step_id)
        except LLMError as e:
            logger.error(f"[DAG-Stream] LLM summarize failed for {step_id}: {e}")
            self.progress_reporter.step_failed(step_id, "summarize", str(e))
            answer = self._fallback_summary(step)
            self._store_summary_result(step, answer)
            return

        answer = "".join(answer_parts)
        _summary_elapsed = time.time() - _summary_t0
        logger.info(f"[DAG] Summarize {step_id} completed in {_summary_elapsed:.2f}s")
        self._store_summary_result(step, answer)


    def _truncate_for_summary(self, sdf: StandardDataFrame, step_id: str) -> str:
        """Apply truncation strategy for data passed to summary LLM."""
        if sdf.metadata.row_count <= Config.MAX_ROWS_FOR_SUMMARY:
            return sdf.to_display_text()

        # Determine truncation type
        has_rank_op = False
        has_time_dim = False

        for col in sdf.columns:
            if col.data_type in ("date", "datetime") or col.name in ("month", "year", "date", "quarter"):
                has_time_dim = True

        if has_rank_op:
            head = Config.TRUNCATE_RANK_HEAD
            tail = Config.TRUNCATE_RANK_TAIL
        elif has_time_dim:
            head = Config.TRUNCATE_TIMESERIES_HEAD
            tail = Config.TRUNCATE_TIMESERIES_TAIL
        else:
            head = Config.TRUNCATE_HEAD
            tail = Config.TRUNCATE_TAIL

        lines = []
        col_names = [c.name for c in sdf.columns]
        # 0806 fix: 截断路径的表头同样附带中文描述/单位（与 to_display_text 一致）
        header_parts = []
        for c in sdf.columns:
            part = c.name
            meta_bits = []
            if c.description:
                meta_bits.append(c.description)
            if c.unit:
                meta_bits.append(c.unit)
            if meta_bits:
                part += f"({','.join(meta_bits)})"
            header_parts.append(part)
        header = " | ".join(header_parts)
        lines.append(header)
        lines.append("-" * len(header))

        for row in sdf.rows[:head]:
            parts = [str(row.get(cn, "null")) for cn in col_names]
            lines.append(" | ".join(parts))

        omitted = sdf.metadata.row_count - head - tail
        lines.append(f"... (total {sdf.metadata.row_count} rows, {omitted} rows omitted)")

        for row in sdf.rows[-tail:]:
            parts = [str(row.get(cn, "null")) for cn in col_names]
            lines.append(" | ".join(parts))

        # Add statistics
        stats = {}
        for col in sdf.columns:
            if col.role == "measure" and col.data_type in ("float", "int"):
                vals = [row.get(col.name) for row in sdf.rows if row.get(col.name) is not None]
                try:
                    float_vals = [float(v) for v in vals]
                    if float_vals:
                        stats[col.name] = {
                            "min": min(float_vals),
                            "max": max(float_vals),
                            "mean": round(sum(float_vals) / len(float_vals), 2),
                            "sum": round(sum(float_vals), 2),
                        }
                except (ValueError, TypeError):
                    pass

        if stats:
            lines.append(f"\nData summary: {json.dumps(stats, ensure_ascii=False)}")

        return "\n".join(lines)

    def _fallback_summary(self, step: dict) -> str:
        """Generate a fallback summary when LLM fails."""
        parts = [f"针对问题「{self.original_question}」的分析结果：\n"]
        for dep in step.get("depends_on", []):
            if self.context.has_step(dep):
                dep_sdf = self.context.step_outputs[dep]
                parts.append(f"- {dep}: {dep_sdf.metadata.description} ({dep_sdf.metadata.row_count} rows)")
        return "\n".join(parts)

    def _evaluate_condition(self, condition: str) -> bool:
        """Evaluate a condition expression referencing context data."""
        # Simple evaluation: check row_count > 0 patterns
        import re
        match = re.match(r"(step_\d+[\w.]*)\.(row_count|metadata\.row_count)\s*(>|>=|==|<|<=)\s*(\d+)", condition)
        if match:
            ref = match.group(1)
            op = match.group(3)
            threshold = int(match.group(4))
            try:
                sdf = self.context.get(ref)
                actual = sdf.metadata.row_count
                ops = {">": lambda a, b: a > b, ">=": lambda a, b: a >= b, "==": lambda a, b: a == b, "<": lambda a, b: a < b, "<=": lambda a, b: a <= b}
                return ops[op](actual, threshold)
            except Exception:
                return True
        return True

    def _get_final_answer(self, steps: list[dict]) -> str:
        """Extract the final answer from the last summarize step."""
        summarize_steps = [s for s in steps if s["step_type"] == "summarize"]
        if summarize_steps:
            last_summarize = summarize_steps[-1]
            if self.context.has_step(last_summarize["step_id"]):
                sdf = self.context.step_outputs[last_summarize["step_id"]]
                if sdf.rows:
                    return sdf.rows[0].get("answer", "No answer generated")
        return "Analysis completed but no summary was generated."

    def _get_final_data(self, steps: list[dict]) -> Optional[StandardDataFrame]:
        """Get the final data output (usually from the last compute or query step)."""
        for step in reversed(steps):
            if step["step_type"] in ("compute", "query"):
                if self.context.has_step(step["step_id"]):
                    return self.context.step_outputs[step["step_id"]]
        return None

    def _log_step(self, step_id: str, step_type: str, status: str, error_message: Optional[str] = None):
        """Add entry to execution log."""
        self.execution_log.append({
            "step_id": step_id,
            "step_type": step_type,
            "status": status,
            "error_message": error_message,
        })
