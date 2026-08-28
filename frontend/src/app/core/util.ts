import { Signal } from '@angular/core';
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
 * Render a server timestamp (UTC) in the viewer's own locale and time zone, or an em dash when
 * absent. This is the single place UTC→local conversion happens for the whole UI.
 */
export function fmt(dt: string | Date | undefined | null): string {
  if (!dt) {
    return '—';
  }
  const d = dt instanceof Date ? dt : new Date(toUtcInstant(dt));
  if (isNaN(d.getTime())) {
    return String(dt);
  }
  return d.toLocaleString(undefined, DATE_TIME_FORMAT);
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
