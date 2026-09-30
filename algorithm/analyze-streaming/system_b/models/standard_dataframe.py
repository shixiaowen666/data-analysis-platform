"""
StandardDataFrame - Core unified data structure for System B.
All inter-module data transfer uses this format.
"""

from __future__ import annotations
import copy
import json
from typing import Any, Optional


class ColumnDef:
    """Column definition with semantic metadata."""

    def __init__(
        self,
        name: str,
        role: str = "measure",
        data_type: str = "string",
        unit: Optional[str] = None,
        description: str = "",
        source_ref: Optional[str] = None,
        is_computed: bool = False,
        nullable: bool = True,
    ):
        self.name = name
        self.role = role  # dimension | measure | metadata
        self.data_type = data_type  # float | int | string | date | datetime | boolean | null
        self.unit = unit
        self.description = description
        self.source_ref = source_ref
        self.is_computed = is_computed
        self.nullable = nullable

    def to_dict(self) -> dict:
        import re
        cn_name = ""
        if self.description:
            cn_name = re.split(r"\s*=\s*", self.description, maxsplit=1)[0].strip()
        return {
            "name": self.name,
            "role": self.role,
            "data_type": self.data_type,
            "unit": self.unit,
            "description": self.description,
            "cn_name": cn_name,
            "source_ref": self.source_ref,
            "is_computed": self.is_computed,
            "nullable": self.nullable,
        }

    @classmethod
    def from_dict(cls, d: dict) -> "ColumnDef":
        return cls(
            name=d["name"],
            role=d.get("role", "measure"),
            data_type=d.get("data_type", "string"),
            unit=d.get("unit"),
            description=d.get("description", ""),
            source_ref=d.get("source_ref"),
            is_computed=d.get("is_computed", False),
            nullable=d.get("nullable", True),
        )

    def copy(self) -> "ColumnDef":
        return ColumnDef.from_dict(self.to_dict())


class Metadata:
    """Metadata for StandardDataFrame."""

    def __init__(
        self,
        source_step: str = "",
        source_operation: Optional[str] = None,
        description: str = "",
        row_count: int = 0,
        time_range: Optional[dict] = None,
        lineage: Optional[list] = None,
        is_scalar: bool = False,
        tags: Optional[dict] = None,
    ):
        self.source_step = source_step
        self.source_operation = source_operation
        self.description = description
        self.row_count = row_count
        self.time_range = time_range or {}
        self.lineage = lineage or []
        self.is_scalar = is_scalar
        self.tags = tags or {}

    def to_dict(self) -> dict:
        return {
            "source_step": self.source_step,
            "source_operation": self.source_operation,
            "description": self.description,
            "row_count": self.row_count,
            "time_range": self.time_range,
            "lineage": list(self.lineage),
            "is_scalar": self.is_scalar,
            "tags": dict(self.tags),
        }

    @classmethod
    def from_dict(cls, d: dict) -> "Metadata":
        return cls(
            source_step=d.get("source_step", ""),
            source_operation=d.get("source_operation"),
            description=d.get("description", ""),
            row_count=d.get("row_count", 0),
            time_range=d.get("time_range"),
            lineage=d.get("lineage"),
            is_scalar=d.get("is_scalar", False),
            tags=d.get("tags"),
        )


class StandardDataFrame:
    """
    Core unified data structure for System B.
    Contains metadata, column definitions, and row data.
    """

    def __init__(
        self,
        metadata: Optional[Metadata] = None,
        columns: Optional[list[ColumnDef]] = None,
        rows: Optional[list[dict]] = None,
    ):
        self.metadata = metadata or Metadata()
        self.columns = columns or []
        self.rows = rows or []
        self.metadata.row_count = len(self.rows)

    # ---- Convenience constructors ----

    @classmethod
    def from_scalar(
        cls,
        value: Any,
        name: str = "value",
        unit: Optional[str] = None,
        source_step: str = "",
        source_operation: Optional[str] = None,
        description: str = "",
        lineage: Optional[list] = None,
    ) -> "StandardDataFrame":
        """Create a scalar StandardDataFrame (single value)."""
        data_type = _infer_type(value)
        return cls(
            metadata=Metadata(
                source_step=source_step,
                source_operation=source_operation,
                description=description,
                row_count=1,
                is_scalar=True,
                lineage=lineage or [],
            ),
            columns=[
                ColumnDef(
                    name=name,
                    role="measure",
                    data_type=data_type,
                    unit=unit,
                    description=description,
                    is_computed=True,
                )
            ],
            rows=[{name: value}],
        )

    @classmethod
    def empty(cls, columns: Optional[list[ColumnDef]] = None, source_step: str = "", description: str = "") -> "StandardDataFrame":
        """Create an empty StandardDataFrame."""
        return cls(
            metadata=Metadata(source_step=source_step, description=description, row_count=0),
            columns=columns or [],
            rows=[],
        )

    # ---- Serialization ----

    def to_dict(self) -> dict:
        return {
            "metadata": self.metadata.to_dict(),
            "columns": [c.to_dict() for c in self.columns],
            "rows": [dict(r) for r in self.rows],
        }

    @classmethod
    def from_dict(cls, d: dict) -> "StandardDataFrame":
        return cls(
            metadata=Metadata.from_dict(d.get("metadata", {})),
            columns=[ColumnDef.from_dict(c) for c in d.get("columns", [])],
            rows=d.get("rows", []),
        )

    def to_json(self, indent: int = 2) -> str:
        return json.dumps(self.to_dict(), ensure_ascii=False, indent=indent, default=str)

    @classmethod
    def from_json(cls, s: str) -> "StandardDataFrame":
        return cls.from_dict(json.loads(s))

    # ---- Column helpers ----

    def get_column_def(self, name: str) -> Optional[ColumnDef]:
        for c in self.columns:
            if c.name == name:
                return c
        return None

    def get_column_names(self, role: Optional[str] = None) -> list[str]:
        if role:
            return [c.name for c in self.columns if c.role == role]
        return [c.name for c in self.columns]

    def get_dimension_columns(self) -> list[str]:
        return self.get_column_names("dimension")

    def get_measure_columns(self) -> list[str]:
        return self.get_column_names("measure")

    def has_column(self, name: str) -> bool:
        return any(c.name == name for c in self.columns)

    # ---- Data access helpers ----

    def get_column_values(self, column_name: str) -> list:
        return [row.get(column_name) for row in self.rows]

    def get_scalar_value(self) -> Any:
        """Get the scalar value if is_scalar is True."""
        if self.metadata.is_scalar and self.rows:
            measure_cols = self.get_measure_columns()
            if measure_cols:
                return self.rows[0].get(measure_cols[0])
            if self.columns:
                return self.rows[0].get(self.columns[0].name)
        return None

    def is_empty(self) -> bool:
        return len(self.rows) == 0

    # ---- Subset/copy operations ----

    def select_columns(self, col_names: list[str]) -> "StandardDataFrame":
        """Create a new StandardDataFrame with only the specified columns."""
        new_cols = [c.copy() for c in self.columns if c.name in col_names]
        new_rows = [{k: v for k, v in row.items() if k in col_names} for row in self.rows]
        meta = Metadata.from_dict(self.metadata.to_dict())
        meta.row_count = len(new_rows)
        return StandardDataFrame(metadata=meta, columns=new_cols, rows=new_rows)

    def select_with_dimensions(self, measure_name: str) -> "StandardDataFrame":
        """Return a subset with all dimension columns + the specified measure column."""
        dim_cols = self.get_dimension_columns()
        col_names = dim_cols + [measure_name]
        return self.select_columns(col_names)

    def add_column(self, col_def: ColumnDef, values: list) -> "StandardDataFrame":
        """Add a new column. Returns a new StandardDataFrame."""
        sdf = self.deep_copy()
        sdf.columns.append(col_def)
        for i, row in enumerate(sdf.rows):
            row[col_def.name] = values[i] if i < len(values) else None
        return sdf

    def deep_copy(self) -> "StandardDataFrame":
        return StandardDataFrame.from_dict(self.to_dict())

    # ---- Display helpers ----

    def to_display_text(self, max_rows: int = 50) -> str:
        """Convert to human-readable text for LLM prompts.

        0806 fix: 表头带上列的中文 description。此前只输出 `列名(单位)[角色]`，
        总结 LLM 只能按英文列名猜中文含义（copy_num 被猜成"数据复制数量"，
        实际是"抄见数"），导致总结内容与用户问题不相关。
        """
        lines = []
        col_names = [c.name for c in self.columns]
        roles = {c.name: c.role for c in self.columns}
        units = {c.name: c.unit for c in self.columns}
        descs = {c.name: (c.description or "") for c in self.columns}

        header_parts = []
        for cn in col_names:
            part = cn
            meta_bits = []
            if descs.get(cn):
                meta_bits.append(descs[cn])
            if units.get(cn):
                meta_bits.append(units[cn])
            if meta_bits:
                part += f"({','.join(meta_bits)})"
            part += f"[{roles.get(cn, '')}]"
            header_parts.append(part)
        lines.append(" | ".join(header_parts))
        lines.append("-" * len(lines[0]))

        display_rows = self.rows[:max_rows]
        for row in display_rows:
            parts = [str(row.get(cn, "null")) for cn in col_names]
            lines.append(" | ".join(parts))

        if len(self.rows) > max_rows:
            lines.append(f"... (total {len(self.rows)} rows, showing first {max_rows})")

        return "\n".join(lines)

    def __repr__(self) -> str:
        return f"StandardDataFrame(rows={len(self.rows)}, cols={[c.name for c in self.columns]}, scalar={self.metadata.is_scalar})"


def _infer_type(value: Any) -> str:
    """Infer data type string from Python value."""
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "int"
    if isinstance(value, float):
        return "float"
    return "string"


def infer_column_type_from_values(values: list) -> str:
    """Infer column data type from a list of values."""
    non_null = [v for v in values if v is not None]
    if not non_null:
        return "string"
    sample = non_null[0]
    if isinstance(sample, bool):
        return "boolean"
    if isinstance(sample, int):
        return "int"
    if isinstance(sample, float):
        return "float"
    s = str(sample)
    # Check date patterns
    import re
    if re.match(r"^\d{4}-\d{2}-\d{2}$", s):
        return "date"
    if re.match(r"^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}", s):
        return "datetime"
    # Check for comma-formatted numbers (e.g., "42,147.34", "1,029,869.8")
    stripped = s.replace(",", "")
    if re.match(r"^-?\d+\.\d+$", stripped):
        return "float"
    if re.match(r"^-?\d+$", stripped) and "," in s:
        return "int"
    return "string"
