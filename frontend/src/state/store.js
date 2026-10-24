/**
 * frontend/src/state/store.js
 * Centralized reactive store with Undo/Redo snapshot stack for bidirectional AST/CFG editing.
 */
export class BlueprintHistoryStore {
  constructor(limit = 30) {
    this.limit = limit;
    this.undoStack = [];
    this.redoStack = [];
  }
  pushSnapshot(snapshot) {
    this.undoStack.push(JSON.stringify(snapshot));
    if (this.undoStack.length > this.limit) this.undoStack.shift();
    this.redoStack = [];
  }
  undo(currentSnapshot) {
    if (this.undoStack.length === 0) return null;
    this.redoStack.push(JSON.stringify(currentSnapshot));
    return JSON.parse(this.undoStack.pop());
  }
  redo(currentSnapshot) {
    if (this.redoStack.length === 0) return null;
    this.undoStack.push(JSON.stringify(currentSnapshot));
    return JSON.parse(this.redoStack.pop());
  }
}
