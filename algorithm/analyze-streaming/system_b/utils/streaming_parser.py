"""
Streaming Step Parser
=====================
Incrementally extracts individual step objects from an LLM's streaming
JSON output (token-by-token).

Uses buffer.find('"steps"') to locate the steps array, then tracks
brace depth with string-awareness to slice out each complete step JSON
the moment its closing brace arrives.
"""


class StreamStepParser:
    """Incrementally parse step objects from a streaming JSON token flow."""

    def __init__(self):
        self.buffer = ""
        self.pos = 0
        self.state = "SEEK_STEPS"  # SEEK_STEPS -> SEEK_ARRAY -> IN_ARRAY -> IN_STEP -> DONE
        self.step_start = -1
        self.steps = []

    def feed(self, delta: str) -> list:
        """Feed a chunk of tokens, return any newly completed step dicts."""
        self.buffer += delta
        new_steps = []

        while True:
            if self.state == "SEEK_STEPS":
                idx = self.buffer.find('"steps"', self.pos)
                if idx < 0:
                    safe = len(self.buffer) - 7
                    self.pos = max(self.pos, safe) if safe > 0 else self.pos
                    break
                self.pos = idx + 7
                self.state = "SEEK_ARRAY"

            elif self.state == "SEEK_ARRAY":
                found = False
                while self.pos < len(self.buffer):
                    ch = self.buffer[self.pos]
                    if ch == "[":
                        self.pos += 1
                        self.state = "IN_ARRAY"
                        found = True
                        break
                    if ch in " \n\r\t":
                        self.pos += 1
                    elif ch == "{":
                        self.state = "IN_ARRAY"
                        found = True
                        break
                    else:
                        self.pos += 1
                if not found:
                    break

            elif self.state == "IN_ARRAY":
                if self.pos >= len(self.buffer):
                    break
                ch = self.buffer[self.pos]
                if ch in " \n\r\t,":
                    self.pos += 1
                elif ch == "{":
                    self.step_start = self.pos
                    self.pos += 1
                    self.state = "IN_STEP"
                    self._depth = 1
                    self._in_str = False
                    self._esc = False
                elif ch == "]":
                    self.pos += 1
                    self.state = "DONE"
                    break
                else:
                    self.pos += 1

            elif self.state == "IN_STEP":
                while self.pos < len(self.buffer):
                    ch = self.buffer[self.pos]
                    if self._esc:
                        self._esc = False
                        self.pos += 1
                        continue
                    if ch == "\\":
                        self._esc = True
                        self.pos += 1
                        continue
                    if ch == '"':
                        self._in_str = not self._in_str
                        self.pos += 1
                        continue
                    if not self._in_str:
                        if ch == "{":
                            self._depth += 1
                        elif ch == "}":
                            self._depth -= 1
                            if self._depth == 0:
                                snippet = self.buffer[self.step_start:self.pos + 1]
                                try:
                                    import json
                                    step = json.loads(snippet)
                                    self.steps.append(step)
                                    new_steps.append(step)
                                except Exception:
                                    pass
                                self.pos += 1
                                self.state = "IN_ARRAY"
                                break
                    self.pos += 1
                if self.state == "IN_STEP":
                    break

            elif self.state == "DONE":
                break

        return new_steps

    def finalize(self) -> list:
        """Called after the stream ends. Does a full-text json.loads cross-check."""
        import json
        try:
            data = json.loads(self.buffer)
            full_steps = data.get("steps", [])
            if len(full_steps) != len(self.steps):
                import logging
                logging.getLogger(__name__).warning(
                    f"StreamStepParser mismatch: incremental={len(self.steps)}, "
                    f"full-text={len(full_steps)}; using full-text"
                )
                self.steps = full_steps
            return self.steps
        except Exception:
            return self.steps
