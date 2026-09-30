"""
Arithmetic Functions: add, subtract, multiply, divide
"""

import logging
from typing import Optional

from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata

logger = logging.getLogger(__name__)


def _align_and_compute(
    col_a_input, col_b_input, operation: str, output_name: str, output_unit: Optional[str],
    join_keys: Optional[list] = None, multiply_by_100: bool = False,
) -> StandardDataFrame:
    """
    Core alignment and computation engine for arithmetic operations.
    Handles: scalar-scalar, scalar-df, df-scalar, df-df (same or different sources).

    multiply_by_100: when True (typically for growth-rate divisions), the result
        is scaled by 100 so it reads as a percentage (e.g. 0.16 -> 16.0). This is
        commonly requested in plans via inputs.multiply_by_100=true together with
        output_unit="%".
    """
    scale = 100.0 if multiply_by_100 else 1.0
    a_is_scalar = isinstance(col_a_input, StandardDataFrame) and col_a_input.metadata.is_scalar
    b_is_scalar = isinstance(col_b_input, StandardDataFrame) and col_b_input.metadata.is_scalar
    a_is_numeric = isinstance(col_a_input, (int, float))
    b_is_numeric = isinstance(col_b_input, (int, float))

    if a_is_numeric:
        col_a_input = StandardDataFrame.from_scalar(col_a_input, name="col_a")
        a_is_scalar = True
    if b_is_numeric:
        col_b_input = StandardDataFrame.from_scalar(col_b_input, name="col_b")
        b_is_scalar = True

    a_sdf = col_a_input
    b_sdf = col_b_input

    # ROBUSTNESS (empty-operand guard): When one operand is an EMPTY SDF (0 rows,
    # non-scalar) — e.g. a baseline like "宝安供电局" that an upstream filter could
    # not match (because the data only contains its sub-bureaus) — we must NOT
    # raise. Treat the empty side as a missing scalar value (None) so the
    # arithmetic broadcasts over the populated side and yields a result column
    # full of None instead of dropping the column entirely. This keeps the
    # compute step's output (and its measure column) intact so downstream
    # summarize can explain "baseline unavailable" rather than "no result".
    def _is_empty_sdf(x):
        return isinstance(x, StandardDataFrame) and not x.metadata.is_scalar and len(x.rows) == 0

    a_empty = _is_empty_sdf(a_sdf)
    b_empty = _is_empty_sdf(b_sdf)
    if a_empty and b_empty:
        # Both sides missing -> single None scalar result.
        logger.warning(
            f"[{operation}] both operands are empty; returning a single null "
            f"'{output_name}' value"
        )
        return StandardDataFrame.from_scalar(
            value=None, name=output_name, unit=output_unit,
            description=f"{operation}(empty, empty) = None",
        )
    if b_empty and not a_empty:
        # Broadcast a missing baseline (None) across side A's rows.
        a_measures = a_sdf.get_measure_columns()
        a_col = a_measures[-1] if a_measures else a_sdf.columns[-1].name
        logger.warning(
            f"[{operation}] right operand is empty (missing baseline); "
            f"output '{output_name}' will be null for all {len(a_sdf.rows)} rows"
        )
        result_rows = []
        for row in a_sdf.rows:
            new_row = dict(row)
            new_row[output_name] = None
            result_rows.append(new_row)
        return _build_result_sdf(a_sdf, result_rows, output_name, output_unit, operation)
    if a_empty and not b_empty:
        b_measures = b_sdf.get_measure_columns()
        b_col = b_measures[-1] if b_measures else b_sdf.columns[-1].name
        logger.warning(
            f"[{operation}] left operand is empty; output '{output_name}' "
            f"will be null for all {len(b_sdf.rows)} rows"
        )
        result_rows = []
        for row in b_sdf.rows:
            new_row = dict(row)
            new_row[output_name] = None
            result_rows.append(new_row)
        return _build_result_sdf(b_sdf, result_rows, output_name, output_unit, operation)

    # BUGFIX (issue #6): When a column reference like "step_1.td_gen_energy"
    # resolves to a 1-row SDF carrying its dimension columns, and the other
    # side is also 1-row but from a different source (so the dimension values
    # differ), treat both sides as scalars (use the measure value directly).
    # Without this, the join below produces no match and the result is None.
    if (not a_is_scalar) and isinstance(a_sdf, StandardDataFrame) and len(a_sdf.rows) == 1 \
            and (not b_is_scalar) and isinstance(b_sdf, StandardDataFrame) and len(b_sdf.rows) == 1:
        a_dims = set(a_sdf.get_dimension_columns())
        b_dims = set(b_sdf.get_dimension_columns())
        common = a_dims & b_dims
        same_source = a_sdf.metadata.source_step == b_sdf.metadata.source_step
        # Either no common dims, or common dim values disagree -> degrade to scalar.
        dim_values_match = True
        if common:
            for k in common:
                if a_sdf.rows[0].get(k) != b_sdf.rows[0].get(k):
                    dim_values_match = False
                    break
        if not same_source and (not common or not dim_values_match):
            a_meas = a_sdf.get_measure_columns()
            b_meas = b_sdf.get_measure_columns()
            if a_meas and b_meas:
                a_is_scalar = True
                b_is_scalar = True
                a_sdf = StandardDataFrame.from_scalar(
                    a_sdf.rows[0].get(a_meas[-1]), name=a_meas[-1],
                    unit=(a_sdf.get_column_def(a_meas[-1]).unit if a_sdf.get_column_def(a_meas[-1]) else None),
                )
                b_sdf = StandardDataFrame.from_scalar(
                    b_sdf.rows[0].get(b_meas[-1]), name=b_meas[-1],
                    unit=(b_sdf.get_column_def(b_meas[-1]).unit if b_sdf.get_column_def(b_meas[-1]) else None),
                )

    # ROBUSTNESS (pseudo-scalar guard): A single-row SDF that has NO dimension
    # columns (only a measure) is semantically a scalar baseline — e.g.
    # extract_dimension("pap_all") on the filtered "宝城供电分局" row yields a
    # 1-row, dimensionless {pap_all: 733625309.18} that was NOT flagged
    # is_scalar=True. When the OTHER side is a multi-row table, the join path
    # finds no common dimension key and raises "Cannot align DataFrames",
    # silently dropping the computed column. Promote such a dimensionless
    # single-row operand to a real scalar so it broadcasts across the table.
    def _is_pseudo_scalar(x):
        return (
            isinstance(x, StandardDataFrame)
            and not x.metadata.is_scalar
            and len(x.rows) == 1
            and len(x.get_dimension_columns()) == 0
            and len(x.get_measure_columns()) >= 1
        )

    if (not a_is_scalar) and (not b_is_scalar):
        if _is_pseudo_scalar(b_sdf) and not _is_pseudo_scalar(a_sdf) and len(a_sdf.rows) != 1:
            b_meas = b_sdf.get_measure_columns()
            mcol = b_meas[-1]
            cdef = b_sdf.get_column_def(mcol)
            b_sdf = StandardDataFrame.from_scalar(
                b_sdf.rows[0].get(mcol), name=mcol,
                unit=(cdef.unit if cdef else None),
            )
            b_is_scalar = True
        elif _is_pseudo_scalar(a_sdf) and not _is_pseudo_scalar(b_sdf) and len(b_sdf.rows) != 1:
            a_meas = a_sdf.get_measure_columns()
            mcol = a_meas[-1]
            cdef = a_sdf.get_column_def(mcol)
            a_sdf = StandardDataFrame.from_scalar(
                a_sdf.rows[0].get(mcol), name=mcol,
                unit=(cdef.unit if cdef else None),
            )
            a_is_scalar = True

    # Get measure columns (the columns to operate on)
    a_measures = a_sdf.get_measure_columns()
    b_measures = b_sdf.get_measure_columns()
    a_col = a_measures[-1] if a_measures else a_sdf.columns[-1].name
    b_col = b_measures[-1] if b_measures else b_sdf.columns[-1].name

    # 0806 fix: 运算结果列的描述用两侧操作数的中文描述组合（"抄见数 除以
    # 应发布表码数"）而非固定英文（"Result of divide"），使总结模型能直接
    # 读懂计算列的业务含义。
    def _op_label(sdf, col):
        cdef = sdf.get_column_def(col)
        return (cdef.description or col) if cdef else col

    _ZH_OP = {"add": "加", "subtract": "减", "multiply": "乘", "divide": "除以"}
    op_description = (
        f"{_op_label(a_sdf, a_col)} {_ZH_OP.get(operation, operation)} "
        f"{_op_label(b_sdf, b_col)}"
        + ("（×100）" if multiply_by_100 else "")
    )

    _base_op = {
        "add": lambda x, y: x + y if x is not None and y is not None else None,
        "subtract": lambda x, y: x - y if x is not None and y is not None else None,
        "multiply": lambda x, y: x * y if x is not None and y is not None else None,
        "divide": lambda x, y: (x / y if y != 0 else None) if x is not None and y is not None else None,
    }[operation]

    def op_func(x, y):
        result = _base_op(x, y)
        if result is not None and scale != 1.0:
            result = result * scale
        return result

    # Case 1: Both scalars
    if a_is_scalar and b_is_scalar:
        a_val = a_sdf.get_scalar_value()
        b_val = b_sdf.get_scalar_value()
        result_val = op_func(_to_float(a_val), _to_float(b_val))
        return StandardDataFrame.from_scalar(
            value=result_val, name=output_name, unit=output_unit,
            description=f"{op_description}（{a_val}, {b_val}）= {result_val}"
        )

    # Case 2: One scalar, one DataFrame -> broadcast
    if a_is_scalar:
        scalar_val = _to_float(a_sdf.get_scalar_value())
        base_sdf = b_sdf
        base_col = b_col
        result_rows = []
        for row in base_sdf.rows:
            new_row = dict(row)
            new_row[output_name] = op_func(scalar_val, _to_float(row.get(base_col)))
            result_rows.append(new_row)
        return _build_result_sdf(base_sdf, result_rows, output_name, output_unit, operation, description=op_description)

    if b_is_scalar:
        scalar_val = _to_float(b_sdf.get_scalar_value())
        base_sdf = a_sdf
        base_col = a_col
        result_rows = []
        for row in base_sdf.rows:
            new_row = dict(row)
            new_row[output_name] = op_func(_to_float(row.get(base_col)), scalar_val)
            result_rows.append(new_row)
        return _build_result_sdf(base_sdf, result_rows, output_name, output_unit, operation, description=op_description)

    # Case 3: Both DataFrames
    # If from same source (same row count and dimensions), row-wise operation.
    # 0723 问题45 fix: "same source_step" does NOT guarantee the two operands
    # have the same ROW ORDER — e.g. two aggregate ops inside one compute step
    # (sum of 2026-06 vs sum of 2025-06, both group_by gdj) inherit different
    # input row orders. Blind positional pairing then subtracts 坪山's current
    # value from 光明's last-year value, silently corrupting every row. If the
    # operands share dimension columns whose value sets overlap, align by KEY;
    # positional pairing is only safe when there are no usable common dims.
    if a_sdf.metadata.source_step == b_sdf.metadata.source_step and len(a_sdf.rows) == len(b_sdf.rows):
        common_dims = [d for d in a_sdf.get_dimension_columns()
                       if d in set(b_sdf.get_dimension_columns())]
        key_aligned = False
        result_rows = []
        if common_dims:
            a_keyset = {tuple(r.get(k) for k in common_dims) for r in a_sdf.rows}
            b_keyset = {tuple(r.get(k) for k in common_dims) for r in b_sdf.rows}
            # Keys must be unique per row on both sides and value sets must
            # overlap, otherwise key alignment is meaningless (e.g. 同期对比
            # with disjoint dates — handled by Case 4's positional fallback).
            if (len(a_keyset) == len(a_sdf.rows) and len(b_keyset) == len(b_sdf.rows)
                    and a_keyset & b_keyset):
                b_index = {tuple(r.get(k) for k in common_dims): r for r in b_sdf.rows}
                for row_a in a_sdf.rows:
                    key = tuple(row_a.get(k) for k in common_dims)
                    row_b = b_index.get(key)
                    new_row = dict(row_a)
                    if row_b is not None:
                        for col_name, val in row_b.items():
                            if col_name not in common_dims or col_name not in new_row:
                                new_row[col_name] = val
                        new_row[output_name] = op_func(
                            _to_float(row_a.get(a_col)), _to_float(row_b.get(b_col)))
                    else:
                        new_row[output_name] = None
                    result_rows.append(new_row)
                key_aligned = True
        if not key_aligned:
            for i, row_a in enumerate(a_sdf.rows):
                row_b = b_sdf.rows[i]
                new_row = dict(row_a)
                new_row.update(row_b)
                new_row[output_name] = op_func(_to_float(row_a.get(a_col)), _to_float(row_b.get(b_col)))
                result_rows.append(new_row)
        # Merge columns from both
        all_cols = list(a_sdf.columns)
        existing_names = {c.name for c in all_cols}
        for c in b_sdf.columns:
            if c.name not in existing_names:
                all_cols.append(c)
        return _build_result_sdf_with_cols(all_cols, result_rows, output_name, output_unit, a_sdf, operation, description=op_description)

    # Case 4: Different sources -> join on dimension keys
    if join_keys:
        keys = join_keys
    else:
        a_dims = set(a_sdf.get_dimension_columns())
        b_dims = set(b_sdf.get_dimension_columns())
        keys = list(a_dims & b_dims)

    # BUGFIX (issue #6): special case - both sides are single-row data from
    # different sources (typical "current period vs previous period" pattern,
    # e.g. step_1.td_gen_energy on 2026-03-12 vs step_2.td_gen_energy on
    # 2026-03-11). The shared dimension (ptdate) has different values on each
    # side, so a join produces no match and the result is silently None.
    # Treat this as a scalar-vs-scalar arithmetic instead.
    if (
        len(a_sdf.rows) == 1
        and len(b_sdf.rows) == 1
        and not (join_keys and all(
            a_sdf.rows[0].get(k) == b_sdf.rows[0].get(k) for k in join_keys
        ))
    ):
        a_val = _to_float(a_sdf.rows[0].get(a_col))
        b_val = _to_float(b_sdf.rows[0].get(b_col))
        result_val = op_func(a_val, b_val)
        # Preserve the dimensions of side A so the result aligns with the
        # "current" period for downstream display.
        new_row = dict(a_sdf.rows[0])
        new_row[output_name] = result_val
        result_rows = [new_row]
        all_cols = list(a_sdf.columns)
        existing_names = {c.name for c in all_cols}
        for c in b_sdf.columns:
            if c.name not in existing_names:
                all_cols.append(c)
        return _build_result_sdf_with_cols(all_cols, result_rows, output_name, output_unit, a_sdf, operation, description=op_description)

    if not keys:
        # No common dimensions, do cross join (rarely needed) - fallback to row-wise if same length
        if len(a_sdf.rows) == len(b_sdf.rows):
            result_rows = []
            for i, row_a in enumerate(a_sdf.rows):
                row_b = b_sdf.rows[i]
                new_row = dict(row_a)
                new_row.update(row_b)
                new_row[output_name] = op_func(_to_float(row_a.get(a_col)), _to_float(row_b.get(b_col)))
                result_rows.append(new_row)
            all_cols = list(a_sdf.columns)
            existing_names = {c.name for c in all_cols}
            for c in b_sdf.columns:
                if c.name not in existing_names:
                    all_cols.append(c)
            return _build_result_sdf_with_cols(all_cols, result_rows, output_name, output_unit, a_sdf, operation, description=op_description)
        raise ValueError(f"Cannot align DataFrames without common dimension keys for {operation}")

    # Build index on b_sdf
    b_index = {}
    for row in b_sdf.rows:
        key = tuple(row.get(k) for k in keys)
        b_index[key] = row

    # 问题6 fix: "同期对比" pattern — both sides share a dimension (e.g. ptdate)
    # but its VALUES are disjoint (2026-06 dates vs 2025-06 dates), so the
    # dimension join matches NOTHING and every result is None. When zero keys
    # match and both sides have the same sorted-by-dimension length, fall back
    # to POSITIONAL alignment (day 1 vs day 1, day 2 vs day 2 ...).
    a_keys = {tuple(r.get(k) for k in keys) for r in a_sdf.rows}
    if a_keys.isdisjoint(b_index.keys()) and a_sdf.rows and b_sdf.rows:
        logger.warning(
            f"[{operation}] dimension keys {keys} have no overlapping values "
            f"between operands (e.g. current-period vs prior-period dates); "
            f"falling back to positional alignment sorted by {keys}"
        )
        a_sorted = sorted(a_sdf.rows, key=lambda r: tuple(str(r.get(k, "")) for k in keys))
        b_sorted = sorted(b_sdf.rows, key=lambda r: tuple(str(r.get(k, "")) for k in keys))
        n_pairs = min(len(a_sorted), len(b_sorted))
        result_rows = []
        for i, row_a in enumerate(a_sorted):
            new_row = dict(row_a)
            if i < n_pairs:
                row_b = b_sorted[i]
                for col_name, val in row_b.items():
                    if col_name not in new_row:
                        new_row[col_name] = val
                    elif col_name in keys:
                        # Keep side A's dim value but expose B's under _right
                        new_row[f"{col_name}_right"] = val
                new_row[output_name] = op_func(_to_float(row_a.get(a_col)), _to_float(row_b.get(b_col)))
            else:
                new_row[output_name] = None
            result_rows.append(new_row)
        all_cols = list(a_sdf.columns)
        existing_names = {c.name for c in all_cols}
        for c in b_sdf.columns:
            if c.name not in existing_names:
                all_cols.append(c)
        return _build_result_sdf_with_cols(all_cols, result_rows, output_name, output_unit, a_sdf, operation, description=op_description)

    result_rows = []
    for row_a in a_sdf.rows:
        key = tuple(row_a.get(k) for k in keys)
        row_b = b_index.get(key)
        new_row = dict(row_a)
        if row_b:
            for col_name, val in row_b.items():
                if col_name not in new_row:
                    new_row[col_name] = val
            new_row[output_name] = op_func(_to_float(row_a.get(a_col)), _to_float(row_b.get(b_col)))
        else:
            new_row[output_name] = None
        result_rows.append(new_row)

    all_cols = list(a_sdf.columns)
    existing_names = {c.name for c in all_cols}
    for c in b_sdf.columns:
        if c.name not in existing_names:
            all_cols.append(c)
    return _build_result_sdf_with_cols(all_cols, result_rows, output_name, output_unit, a_sdf, operation, description=op_description)


def _build_result_sdf(base_sdf: StandardDataFrame, rows: list, output_name: str, output_unit: Optional[str], operation: str, description: Optional[str] = None) -> StandardDataFrame:
    """Build result SDF inheriting columns from base and adding new output column."""
    new_cols = [c.copy() for c in base_sdf.columns]
    new_cols.append(ColumnDef(
        name=output_name, role="measure", data_type="float",
        unit=output_unit, description=description or f"Result of {operation}",
        is_computed=True, nullable=True,
    ))
    meta = Metadata.from_dict(base_sdf.metadata.to_dict())
    meta.row_count = len(rows)
    return StandardDataFrame(metadata=meta, columns=new_cols, rows=rows)


def _build_result_sdf_with_cols(all_cols: list, rows: list, output_name: str, output_unit: Optional[str], ref_sdf: StandardDataFrame, operation: str, description: Optional[str] = None) -> StandardDataFrame:
    """Build result SDF with explicit column list and adding new output column."""
    new_cols = [c.copy() for c in all_cols]
    if not any(c.name == output_name for c in new_cols):
        new_cols.append(ColumnDef(
            name=output_name, role="measure", data_type="float",
            unit=output_unit, description=description or f"Result of {operation}",
            is_computed=True, nullable=True,
        ))
    meta = Metadata.from_dict(ref_sdf.metadata.to_dict())
    meta.row_count = len(rows)
    return StandardDataFrame(metadata=meta, columns=new_cols, rows=rows)


def _to_float(val):
    if val is None:
        return None
    try:
        if isinstance(val, str):
            val = val.replace(",", "")
        return float(val)
    except (ValueError, TypeError):
        return None


# ---- Public API ----

def add(col_a, col_b, output: str = "add_result", output_unit: str = None, join_keys: list = None, multiply_by_100: bool = False, **kwargs) -> StandardDataFrame:
    return _align_and_compute(col_a, col_b, "add", output, output_unit, join_keys, multiply_by_100)


def subtract(col_a, col_b, output: str = "subtract_result", output_unit: str = None, join_keys: list = None, multiply_by_100: bool = False, **kwargs) -> StandardDataFrame:
    return _align_and_compute(col_a, col_b, "subtract", output, output_unit, join_keys, multiply_by_100)


def multiply(col_a, col_b, output: str = "multiply_result", output_unit: str = None, join_keys: list = None, multiply_by_100: bool = False, **kwargs) -> StandardDataFrame:
    return _align_and_compute(col_a, col_b, "multiply", output, output_unit, join_keys, multiply_by_100)


def divide(col_a, col_b, output: str = "divide_result", output_unit: str = None, join_keys: list = None, multiply_by_100: bool = False, **kwargs) -> StandardDataFrame:
    return _align_and_compute(col_a, col_b, "divide", output, output_unit, join_keys, multiply_by_100)
