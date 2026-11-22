import {
  AfterViewInit, Component, ElementRef, OnDestroy, effect, input, model, viewChild,
} from '@angular/core';
import { Compartment, EditorState, Extension } from '@codemirror/state';
import {
  EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter,
} from '@codemirror/view';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import {
  StreamLanguage, bracketMatching, defaultHighlightStyle, indentUnit, syntaxHighlighting,
} from '@codemirror/language';
import { oneDark } from '@codemirror/theme-one-dark';
import { sql } from '@codemirror/lang-sql';
import { python } from '@codemirror/lang-python';
import { java } from '@codemirror/lang-java';
import { shell } from '@codemirror/legacy-modes/mode/shell';

/** The CodeMirror language extension for a cronflower script language. */
function langExtension(language: string): Extension {
  switch ((language || '').toLowerCase()) {
    case 'python':
      return python();
    case 'sql':
      return sql();
    case 'java':
      return java();
    case 'shell':
    default:
      return StreamLanguage.define(shell);
  }
}

/**
 * A code editor for script tasks and DAG nodes, built on CodeMirror 6 with the One Dark theme —
 * bundled with the app (no CDN), so syntax highlighting always works offline. Two-way binds
 * {@link value}; {@link language} picks the grammar (shell/python/sql/java).
 */
@Component({
  selector: 'cf-code-editor',
  template: `<div #host class="cm-host"></div>`,
  styles: [`
    :host { display: block; height: 100%; min-height: 320px; }
    .cm-host { height: 100%; min-height: 320px; border-radius: 10px; overflow: hidden;
      border: 1px solid var(--border); box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.05); }
    :host ::ng-deep .cm-editor { height: 100%; font-size: 0.86rem; }
    :host ::ng-deep .cm-editor.cm-focused { outline: 2px solid color-mix(in oklch, var(--ring) 60%, transparent);
      outline-offset: -1px; }
    :host ::ng-deep .cm-scroller { font-family: var(--cf-font-mono, 'JetBrains Mono', ui-monospace, monospace);
      line-height: 1.6; }
    :host ::ng-deep .cm-gutters { border-right: 1px solid rgba(255,255,255,0.06); }
  `],
})
export class CodeEditor implements AfterViewInit, OnDestroy {
  readonly value = model<string>('');
  readonly language = input<string>('shell');

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private view?: EditorView;
  private readonly langCompartment = new Compartment();
  /** Guards the value->editor sync so our own edits do not loop back through the model. */
  private selfEdit = false;

  constructor() {
    // React to language changes: swap just the language extension, keeping the document.
    effect(() => {
      const lang = this.language();
      if (this.view) {
        this.view.dispatch({ effects: this.langCompartment.reconfigure(langExtension(lang)) });
      }
    });
    // Keep the editor in sync when the value is set from outside (e.g. loading a task to edit).
    effect(() => {
      const v = this.value();
      if (this.view && !this.selfEdit && this.view.state.doc.toString() !== (v ?? '')) {
        this.view.dispatch({
          changes: { from: 0, to: this.view.state.doc.length, insert: v ?? '' },
        });
      }
    });
  }

  ngAfterViewInit(): void {
    const onChange = EditorView.updateListener.of((u) => {
      if (u.docChanged) {
        this.selfEdit = true;
        this.value.set(u.state.doc.toString());
        this.selfEdit = false;
      }
    });
    this.view = new EditorView({
      parent: this.host().nativeElement,
      state: EditorState.create({
        doc: this.value() ?? '',
        extensions: [
          lineNumbers(),
          highlightActiveLine(),
          highlightActiveLineGutter(),
          history(),
          bracketMatching(),
          indentUnit.of('  '),
          keymap.of([...defaultKeymap, ...historyKeymap, indentWithTab]),
          syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
          this.langCompartment.of(langExtension(this.language())),
          oneDark,
          EditorView.lineWrapping,
          onChange,
        ],
      }),
    });
  }

  ngOnDestroy(): void {
    this.view?.destroy();
  }
}
