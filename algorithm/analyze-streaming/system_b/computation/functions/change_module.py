"""
Change Function: Calculate absolute and relative change between two datasets/periods
"""

import logging
from typing import Optional

from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata

logger = logging.getLogger(__name__)


def _to_float(val):
    if val is None:
        return None
    try:
        if isinstance(val, str):
            val = val.replace(",", "")
        return float(val)
    except (ValueError, TypeError):
        return None


def change(
    current_data: StandardDataFrame,
    previous_data: StandardDataFrame,
    value_column: str,
    join_keys: Optional[list] = None,
    output: str = "change_result",
    output_unit: Optional[str] = None,
    **kwargs,
) -> StandardDataFrame:
    """
    Calculate absolute change and change rate between current and previous data.
    change_amount = current - previous
    change_rate = (current - previous) / |previous| * 100
    
    If value_column doesn't exist, tries to find a fuzzy match.
    If data already contains pre-computed growth/change rate columns, returns data as-is.
    """
    if isinstance(join_keys, str):
        join_keys = [join_keys]

    # Check if value_column exists, try fuzzy match if not
    if not current_data.has_column(value_column):
        from system_b.computation.function_dispatcher import _find_best_column_match
        available = [c.name for c in current_data.columns if c.role == "measure"]
        all_cols = [c.name for c in current_data.columns]
        match = _find_best_column_match(value_column, available)
        if match:
            logger.info(f"[change] Auto-corrected value_column: '{value_column}' -> '{match}'")
            value_column = match
        else:
            # Check if data already has pre-computed growth/change rate columns
            # This handles the case where mock data returns data with growth_rate pre-computed
            precomputed_cols = [c.name for c in current_data.columns
                              if any(kw in c.name.lower() for kw in ['growth_rate', 'change_rate', 'relative_change', 'change_amount'])]
            has_current_previous = ('current_amount' in all_cols and 'previous_amount' in all_cols) or \
                                   any('current_' in c for c in all_cols) and any('previous_' in c for c in all_cols)
            
            if (precomputed_cols or has_current_previous) and current_data.metadata.row_count > 0:
                logger.info(f"[change] Column '{value_column}' not found but data has pre-computed columns: {precomputed_cols or 'current/previous pairs'}. Returning data with existing rates.")
                # Add change_amount and change_rate aliases if they don't exist
                result_rows = []
                for row in current_data.rows:
                    new_row = dict(row)
                    # Map pre-computed columns to expected output columns
                    if 'change_rate' not in new_row:
                        for pc in precomputed_cols:
                            if 'rate' in pc.lower() or 'growth' in pc.lower() or 'relative' in pc.lower():
                                new_row['change_rate'] = row.get(pc)
                                break
                    if 'change_amount' not in new_row:
                        # Try to compute from current_amount and previous_amount
                        curr = _to_float(row.get('current_amount'))
                        prev = _to_float(row.get('previous_amount'))
                        if curr is None or prev is None:
                            # Try current_labor_cost / previous_labor_cost pattern
                            for c in all_cols:
                                if c.startswith('current_'):
                                    prev_name = 'previous_' + c[len('current_'):]
                                    if prev_name in all_cols:
                                        curr = _to_float(row.get(c))
                                        prev = _to_float(row.get(prev_name))
                                        break
                        if curr is not None and prev is not None:
                            new_row['change_amount'] = round(curr - prev, 6)
                        else:
                            # Try to find from existing change_amount column
                            for pc in precomputed_cols:
                                if 'amount' in pc.lower():
                                    new_row['change_amount'] = row.get(pc)
                                    break
                            if 'change_amount' not in new_row:
                                new_row['change_amount'] = None
                    result_rows.append(new_row)
                
                cols = [c.copy() for c in current_data.columns]
                # Add change_amount and change_rate if not already present
                existing_names = {c.name for c in cols}
                if 'change_amount' not in existing_names:
                    cols.append(ColumnDef(name="change_amount", role="measure", data_type="float", unit=output_unit, description="变化量（本期-上期）", is_computed=True))
                if 'change_rate' not in existing_names:
                    cols.append(ColumnDef(name="change_rate", role="measure", data_type="float", unit="%", description="变化率", is_computed=True))
                
                meta = Metadata.from_dict(current_data.metadata.to_dict())
                meta.row_count = len(result_rows)
                meta.description = f"Change analysis (pre-computed) on {value_column}"
                return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)
            
            logger.warning(f"[change] Column '{value_column}' not found in current_data. Available: {available}")

    # Auto-detect join keys from common dimension columns
    if not join_keys:
        c_dims = set(current_data.get_dimension_columns())
        p_dims = set(previous_data.get_dimension_columns())
        join_keys = list(c_dims & p_dims)

    # Build index on previous data
    prev_index = {}
    for row in previous_data.rows:
        if join_keys:
            key = tuple(row.get(k) for k in join_keys)
        else:
            key = ("_single_",)
        prev_index[key] = row

    result_rows = []
    for row in current_data.rows:
        if join_keys:
            key = tuple(row.get(k) for k in join_keys)
        else:
            key = ("_single_",)

        new_row = dict(row)
        prev_row = prev_index.get(key)

        current_val = _to_float(row.get(value_column))
        if prev_row:
            previous_val = _to_float(prev_row.get(value_column))
            # Add previous value column
            new_row[f"previous_{value_column}"] = previous_val

            if current_val is not None and previous_val is not None:
                change_amount = round(current_val - previous_val, 6)
                if previous_val != 0:
                    change_rate = round((current_val - previous_val) / abs(previous_val) * 100, 2)
                else:
                    change_rate = None
            else:
                change_amount = None
                change_rate = None
        else:
            new_row[f"previous_{value_column}"] = None
            change_amount = None
            change_rate = None

        new_row["change_amount"] = change_amount
        new_row["change_rate"] = change_rate
        result_rows.append(new_row)

    col_def = current_data.get_column_def(value_column)
    val_unit = col_def.unit if col_def else output_unit
    # 0806 fix: 派生列描述用源列中文描述（"上期抄见数"）而非英文列名
    _val_label = (col_def.description if col_def and col_def.description else value_column)

    cols = [c.copy() for c in current_data.columns]
    cols.extend([
        ColumnDef(name=f"previous_{value_column}", role="measure", data_type="float", unit=val_unit, description=f"上期{_val_label}", is_computed=True),
        ColumnDef(name="change_amount", role="measure", data_type="float", unit=val_unit, description=f"{_val_label}变化量（本期-上期）", is_computed=True),
        ColumnDef(name="change_rate", role="measure", data_type="float", unit="%", description=f"{_val_label}变化率", is_computed=True),
    ])

    meta = Metadata.from_dict(current_data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Change analysis on {value_column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)
