import { signal, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable, switchMap, timer } from 'rxjs';

/** Poll an endpoint every `ms` and expose it as a signal (first emission immediate). */
export function poll<T>(source: () => Observable<T>, ms = 5000): Signal<T | undefined> {
  return toSignal(timer(0, ms).pipe(switchMap(() => source())));
}

const STATUS_CLASS: Record<string, string> = {
  STANDBY: 'st-standby',
  SCHEDULED: 'st-scheduled',
  RUNNING: 'st-running',
  PAUSED: 'st-paused',
  FINISHED: 'st-finished',
  CANCELED: 'st-canceled',
  NONE: 'st-none',
};

export function statusClass(status: string | undefined | null): string {
  return STATUS_CLASS[status ?? 'NONE'] ?? 'st-none';
}

/**
 * The server stores and returns timestamps in UTC, as ISO strings with no zone designator
 * (e.g. "2026-08-26T12:54:04.340495"). JavaScript would parse such a string as *local* time, which
 * silently shifts every displayed time by the viewer's offset. Tag it as UTC so it is anchored
 * correctly; a string that already carries a zone (Z or ±hh:mm) is left untouched.
 */
function toUtcInstant(value: string): string {
  const s = value.trim();
  const hasZone = /[zZ]$/.test(s) || /[+-]\d{2}:?\d{2}$/.test(s);
  return s.includes('T') && !hasZone ? s + 'Z' : s;
}

const DATE_TIME_FORMAT: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
};

/**
 * How the UI renders timestamps. The server works entirely in UTC, so 'utc' shows the raw server
 * value and 'local' converts it to the viewer's own time zone. UTC is the default so what the
 * console shows always matches what the scheduler stored; the viewer can opt into local time.
 */
export type TzMode = 'utc' | 'local';
const TZ_KEY = 'cf.tzMode';

function loadTzMode(): TzMode {
  try {
    return localStorage.getItem(TZ_KEY) === 'local' ? 'local' : 'utc';
  } catch {
    return 'utc';
  }
}

/** The active display mode, as a signal so templates re-render the instant it is toggled. */
export const tzMode = signal<TzMode>(loadTzMode());

export function setTzMode(mode: TzMode): void {
  tzMode.set(mode);
  try {
    localStorage.setItem(TZ_KEY, mode);
  } catch {
    /* private mode / storage disabled — the in-memory signal still drives the UI */
  }
}

/**
 * Render a server timestamp (always UTC) either as UTC or in the viewer's local zone, per the
 * active {@link tzMode}, or an em dash when absent. This is the single place that conversion
 * happens for the whole UI; reading the tzMode signal here makes every `fmt()` call reactive.
 */
export function fmt(dt: string | Date | undefined | null): string {
  if (!dt) {
    return '—';
  }
  const d = dt instanceof Date ? dt : new Date(toUtcInstant(dt));
  if (isNaN(d.getTime())) {
    return String(dt);
  }
  const opts: Intl.DateTimeFormatOptions =
    tzMode() === 'utc' ? { ...DATE_TIME_FORMAT, timeZone: 'UTC' } : DATE_TIME_FORMAT;
  return d.toLocaleString(undefined, opts);
}

const PAD = (n: number): string => String(n).padStart(2, '0');

/**
 * Convert a UTC server timestamp into the value an `<input type="datetime-local">` expects
 * (YYYY-MM-DDTHH:mm:ss), honouring the active {@link tzMode}. In UTC mode the raw value is shown;
 * in local mode it is converted to the viewer's wall-clock time.
 */
export function toInputDateTime(utc: string | null | undefined): string {
  if (!utc) {
    return '';
  }
  if (tzMode() === 'utc') {
    return utc.slice(0, 19);
  }
  const d = new Date(toUtcInstant(utc));
  if (isNaN(d.getTime())) {
    return utc.slice(0, 19);
  }
  return `${d.getFullYear()}-${PAD(d.getMonth() + 1)}-${PAD(d.getDate())}T${PAD(d.getHours())}:${PAD(d.getMinutes())}:${PAD(d.getSeconds())}`;
}

/**
 * Inverse of {@link toInputDateTime}: turn a datetime-local value entered in the active mode into
 * the naive UTC string the server stores. In UTC mode the value is already UTC wall-clock; in
 * local mode it is parsed as local time and converted to UTC.
 */
export function fromInputDateTime(value: string | null | undefined): string {
  const v = (value ?? '').trim();
  if (!v) {
    return '';
  }
  if (tzMode() === 'utc') {
    return v;
  }
  const d = new Date(v); // a zone-less datetime-local string is parsed as local time
  if (isNaN(d.getTime())) {
    return v;
  }
  return d.toISOString().slice(0, 19);
}

/** The viewer's IANA time-zone name (e.g. "Australia/Sydney"), for a "times shown in …" hint. */
export function localZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'local time';
  } catch {
    return 'local time';
  }
}

/** A short UTC-offset label for the viewer's zone (e.g. "UTC+10"). */
export function localZoneOffset(): string {
  const minutes = -new Date().getTimezoneOffset();
  const sign = minutes >= 0 ? '+' : '−';
  const abs = Math.abs(minutes);
  const h = Math.floor(abs / 60);
  const m = abs % 60;
  return `UTC${sign}${h}${m ? ':' + String(m).padStart(2, '0') : ''}`;
}

/** Short label for the active mode, e.g. "UTC" or "Australia/Sydney · UTC+10". */
export function tzLabel(): string {
  return tzMode() === 'utc' ? 'UTC' : `${localZone()} · ${localZoneOffset()}`;
}
