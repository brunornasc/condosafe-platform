import { computed, onUnmounted, ref } from 'vue';
import type { ComputedRef, Ref } from 'vue';

export const TOTP_STEP_SECONDS = 30;

export interface TotpTimer {
  /** Segundos restantes no slot atual (contagem regressiva, estilo Google Authenticator) */
  remaining: Ref<number>;
  /** Fração 0..1 de tempo já consumida no slot (para animar o anel) */
  progress: ComputedRef<number>;
  /** Epoch do slot TOTP atual (muda a cada 30s) */
  slot: Ref<number>;
  start: (onSlotBoundary: () => void) => void;
  stop: () => void;
}

/**
 * Timer TOTP sincronizado com slots de 30s (alinhado ao epoch, sem drift).
 * O `progress` alimenta o arco do anel SVG; quando o slot muda, dispara o
 * callback para regenerar o payload do QR Code offline.
 */
export function useTotpTimer(stepSeconds = TOTP_STEP_SECONDS): TotpTimer {
  const remaining = ref(stepSeconds);
  const elapsed = ref(0);
  const slot = ref(Math.floor(Date.now() / 1000 / stepSeconds));

  const progress = computed(() => elapsed.value / stepSeconds);

  let timerId: number | undefined;
  let onSlotBoundary: (() => void) | undefined;

  function refresh(): void {
    const epochNow = Math.floor(Date.now() / 1000);
    const currentSlot = Math.floor(epochNow / stepSeconds);
    const secondsIntoSlot = epochNow - currentSlot * stepSeconds;

    if (currentSlot !== slot.value) {
      slot.value = currentSlot;
      onSlotBoundary?.();
    }

    elapsed.value = secondsIntoSlot;
    remaining.value = stepSeconds - secondsIntoSlot;
  }

  function start(callback: () => void): void {
    onSlotBoundary = callback;
    refresh();
    timerId = window.setInterval(refresh, 250);
  }

  function stop(): void {
    if (timerId !== undefined) {
      window.clearInterval(timerId);
    }
    timerId = undefined;
  }

  onUnmounted(stop);

  return { remaining, progress, slot, start, stop };
}