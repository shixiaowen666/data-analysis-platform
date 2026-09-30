"""
Custom exception classes for System B.
"""


class SystemBError(Exception):
    """Base exception for System B."""
    pass


class SystemAConnectionError(SystemBError):
    """System A communication failure."""
    pass


class SystemATimeoutError(SystemBError):
    """System A response timeout."""
    pass


class SystemAQueryError(SystemBError):
    """System A returned an error for a query."""
    pass


class StepDecompositionError(SystemBError):
    """Failed to decompose user query into steps."""

    def __init__(self, message, system_prompt="", user_prompt=""):
        super().__init__(message)
        self.system_prompt = system_prompt
        self.user_prompt = user_prompt


class ReferenceResolveError(SystemBError):
    """Failed to resolve a data reference."""
    pass


class ComputationError(SystemBError):
    """Error during computation function execution."""
    pass


class DAGExecutionError(SystemBError):
    """Error during DAG execution."""
    pass


class LLMError(SystemBError):
    """Error during LLM API call."""
    pass