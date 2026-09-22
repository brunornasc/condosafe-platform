<template>
  <ion-page>
    <ion-header translucent>
      <ion-toolbar>
        <ion-title>CondoSafe Acesso</ion-title>
        <ion-badge
          slot="end"
          class="banner"
          :color="isOnline ? 'success' : 'warning'"
        >
          {{ isOnline ? 'Online' : 'Offline' }}
        </ion-badge>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <div v-if="status === 'loading'" class="center">
        <ion-spinner name="crescent" />
      </div>

      <div v-else-if="status === 'error'" class="center msg">
        <ion-icon :icon="cloudOfflineOutline" size="large" color="danger" />
        <h2>Sem credenciais locais</h2>
        <p>{{ errorMessage }}</p>
        <ion-button expand="block" @click="boot">Tentar novamente</ion-button>
      </div>

      <div v-else class="qr-screen">
        <ion-text color="medium" class="resident-label">
          Morador &middot; {{ unitIdShort }}
        </ion-text>

        <div class="totp">
          <svg class="ring" viewBox="0 0 280 280" :class="{ offline: !isOnline }">
            <circle class="ring__track" cx="140" cy="140" r="130" />
            <circle
              class="ring__arc"
              cx="140"
              cy="140"
              r="130"
              :style="{ strokeDashoffset: `${ringDashOffset}px` }"
            />
          </svg>

          <div class="totp__qr">
            <canvas ref="qrCanvas" width="200" height="200" />
          </div>

          <div class="totp__countdown">
            <span class="totp__seconds">{{ remaining }}</span>
            <span class="totp__unit">seg</span>
          </div>
        </div>

        <ion-text color="medium" class="unit-label">
          Unidade {{ unitIdShort }} &middot; QR válido por 30s
        </ion-text>
        <ion-text v-if="!isOnline" color="warning" class="offline-label">
          Modo offline — assinatura HMAC gerada localmente
        </ion-text>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { CryptoBridge } from '@condosafe/crypto-bridge';
import {
  IonBadge,
  IonButton,
  IonContent,
  IonHeader,
  IonIcon,
  IonPage,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/vue';
import { cloudOfflineOutline } from 'ionicons/icons';
import QRCode from 'qrcode';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { useResidentProvisioning } from '../composables/useResidentProvisioning';
import { useTotpTimer } from '../composables/useTotpTimer';
import type { GeneratedQrPayload } from '../types';

const RING_RADIUS = 130;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;
const QR_SIZE = 200;

const { provisioning, refresh, restoreFromCache } = useResidentProvisioning();
const { remaining, progress, start: startTimer } = useTotpTimer();

const qrCanvas = ref<HTMLCanvasElement | null>(null);
const payload = ref<GeneratedQrPayload | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const isOnline = ref(typeof navigator !== 'undefined' ? navigator.onLine : true);

const ringDashOffset = computed(() => RING_CIRCUMFERENCE * progress.value);
const unitIdShort = computed(() => provisioning.value?.unitId.slice(0, 8) ?? '—');

async function generateForCurrentSlot(): Promise<void> {
  const current = provisioning.value;
  if (!current) {
    return;
  }
  const generated = await CryptoBridge.generateQrPayload({
    subjectId: current.userId,
    unitId: current.unitId,
    secretKey: current.secretKey,
  });
  payload.value = generated;
  await renderQr(generated);
}

async function renderQr(generated: GeneratedQrPayload): Promise<void> {
  const canvas = qrCanvas.value;
  if (!canvas) {
    return;
  }
  await QRCode.toCanvas(canvas, generated.rawJson, {
    width: QR_SIZE,
    margin: 1,
    errorCorrectionLevel: 'M',
    color: { dark: '#101418', light: '#ffffff' },
  });
}

/**
 * Bootstrap do ciclo de vida:
 * 1. Restaura o segredo do SecureStorage/Vault (offline-first).
 * 2. Se online, re-provisiona via /access/qr-token (rotação segura).
 * 3. Gera o primeiro payload e aguarda a virada do slot TOTP.
 */
async function boot(): Promise<void> {
  status.value = 'loading';

  await restoreFromCache();

  if (!provisioning.value && isOnline.value) {
    await refresh();
  }

  if (provisioning.value) {
    status.value = 'ready';
    await generateForCurrentSlot();
  } else {
    status.value = 'error';
    errorMessage.value =
      'Nenhum segredo de assinatura encontrado localmente. Conecte-se uma vez para provisionar o acesso offline.';
  }
}

async function handleOnline(): Promise<void> {
  isOnline.value = true;
  if (provisioning.value) {
    // Re-provisiona (segredo pode ter rotacionado) e dessincroniza o QR
    await refresh();
  }
  if (status.value === 'ready') {
    await generateForCurrentSlot();
  } else {
    await boot();
  }
}

function handleOffline(): void {
  isOnline.value = false;
}

onMounted(() => {
  window.addEventListener('online', handleOnline);
  window.addEventListener('offline', handleOffline);
  startTimer(() => {
    if (status.value === 'ready') {
      void generateForCurrentSlot();
    }
  });
  void boot();
});

onBeforeUnmount(() => {
  window.removeEventListener('online', handleOnline);
  window.removeEventListener('offline', handleOffline);
});
</script>

<style scoped>
.center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 100%;
  padding: 24px;
  text-align: center;
}

.banner {
  margin-right: 12px;
}

.qr-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 100%;
  padding: 16px;
}

.resident-label {
  font-size: 0.95rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.totp {
  position: relative;
  width: 280px;
  height: 280px;
}

.ring {
  position: absolute;
  inset: 0;
  transform: rotate(-90deg);
}

.ring__track {
  fill: none;
  stroke: var(--ion-color-step-200, #ececec);
  stroke-width: 10;
}

.ring__arc {
  fill: none;
  stroke: var(--ion-color-primary, #3880ff);
  stroke-width: 10;
  stroke-linecap: round;
  stroke-dasharray: v-bind(RING_CIRCUMFERENCE + 'px');
  transition: stroke-dashoffset 0.25s linear;
}

.totp.offline .ring__arc {
  stroke: var(--ion-color-warning, #ffc409);
}

.totp__qr {
  position: absolute;
  top: 42px;
  left: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 196px;
  height: 196px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.totp__qr canvas {
  width: 180px;
  height: 180px;
  border-radius: 6px;
}

.totp__countdown {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  display: flex;
  align-items: baseline;
  gap: 4px;
  padding: 2px 12px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 999px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.totp__seconds {
  font-size: 1.25rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--ion-color-primary, #3880ff);
}

.totp.offline .totp__seconds {
  color: var(--ion-color-warning, #ffc409);
}

.totp__unit {
  font-size: 0.75rem;
  color: var(--ion-color-medium, #92949c);
}

.unit-label {
  font-size: 0.875rem;
}

.offline-label {
  font-size: 0.8rem;
}
</style>