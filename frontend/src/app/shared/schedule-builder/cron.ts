export type Freq =
  | 'seconds' | 'minutes' | 'hourly' | 'daily' | 'weekly' | 'monthly' | 'yearly' | 'advanced';

export interface CronSpec {
  freq: Freq;
  everyN: number;
  second: number;
  minute: number;
  hour: number;
  days: string[];
  dayOfMonth: number;
  month: string;
  adv: { second: string; minute: string; hour: string; dom: string; mon: string; dow: string };
}

function n(v: number, min: number, max: number): number {
  const x = Math.floor(Number(v));
  if (isNaN(x)) return min;
  return Math.max(min, Math.min(max, x));
}

/** Build a Quartz 6-field cron (second minute hour day-of-month month day-of-week). */
export function buildCron(s: CronSpec): string {
  const sec = n(s.second, 0, 59);
  const min = n(s.minute, 0, 59);
  const hr = n(s.hour, 0, 23);
  const every = n(s.everyN, 1, 59);

  switch (s.freq) {
    case 'seconds':
      return `*/${n(every, 1, 59)} * * * * ?`;
    case 'minutes':
      return `0 */${n(every, 1, 59)} * * * ?`;
    case 'hourly':
      return `0 ${min} */${n(every, 1, 23)} * * ?`;
    case 'daily': {
      const dom = every > 1 ? `*/${n(every, 1, 31)}` : '*';
      return `${sec} ${min} ${hr} ${dom} * ?`;
    }
    case 'weekly': {
      const days = s.days.length ? s.days.join(',') : 'MON';
      return `${sec} ${min} ${hr} ? * ${days}`;
    }
    case 'monthly':
      return `${sec} ${min} ${hr} ${n(s.dayOfMonth, 1, 31)} * ?`;
    case 'yearly':
      return `${sec} ${min} ${hr} ${n(s.dayOfMonth, 1, 31)} ${s.month || 'JAN'} ?`;
    case 'advanced': {
      const a = s.adv;
      return `${a.second || '0'} ${a.minute || '0'} ${a.hour || '*'} ${a.dom || '*'} ${a.mon || '*'} ${a.dow || '?'}`
        .replace(/\s+/g, ' ')
        .trim();
    }
  }
}

const TIME = (s: CronSpec) =>
  `${String(n(s.hour, 0, 23)).padStart(2, '0')}:${String(n(s.minute, 0, 59)).padStart(2, '0')}` +
  `:${String(n(s.second, 0, 59)).padStart(2, '0')}`;

/** A short plain-English label for the current builder state (not a general cron parser). */
export function describeSpec(s: CronSpec): string {
  const e = n(s.everyN, 1, 59);
  switch (s.freq) {
    case 'seconds': return `Every ${e} second(s).`;
    case 'minutes': return `Every ${e} minute(s).`;
    case 'hourly': return `Every ${n(e, 1, 23)} hour(s), at minute ${n(s.minute, 0, 59)}.`;
    case 'daily': return e > 1 ? `Every ${e} days at ${TIME(s)}.` : `Every day at ${TIME(s)}.`;
    case 'weekly': return `Every ${s.days.length ? s.days.join(', ') : 'Mon'} at ${TIME(s)}.`;
    case 'monthly': return `On day ${n(s.dayOfMonth, 1, 31)} of every month at ${TIME(s)}.`;
    case 'yearly': return `Every ${s.month} ${n(s.dayOfMonth, 1, 31)} at ${TIME(s)}.`;
    case 'advanced': return 'Custom cron expression.';
  }
}

/** Humanize an ISO-8601 duration like PT1H30M -> "every 1h 30m". */
export function describeIso(iso: string): string {
  const m = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/i.exec(iso.trim());
  if (!m) return '';
  const parts = [
    m[1] && `${m[1]}d`, m[2] && `${m[2]}h`, m[3] && `${m[3]}m`, m[4] && `${m[4]}s`,
  ].filter(Boolean);
  return parts.length ? `Every ${parts.join(' ')}.` : '';
}
