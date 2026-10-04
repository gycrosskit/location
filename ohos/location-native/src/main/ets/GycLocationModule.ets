import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { LocationClient, LocationOptions, LocationRequest } from './LocationClient';

interface RequestParams {
  requestId: number;
  timeoutMillis: number;
  maxAgeMillis: number;
  maxAccuracyMeters: number;
}

/** 单笔定位桥；旧取消回执不能终止后继请求。 */
export class GycLocationModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME: string = 'GycLocationModule';
  private readonly client: LocationClient = new LocationClient();
  private active: LocationRequest | null = null;
  private activeId: number = 0;
  private destroyed: boolean = false;
  syncMode(): boolean { return false; }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    if (this.destroyed) return null;
    try {
      const args = JSON.parse(typeof params === 'string' ? params : '{}') as RequestParams;
      if (!args || !Number.isSafeInteger(args.requestId) || args.requestId <= 0) throw new Error('Invalid request');
      if (method === 'cancelLocation') {
        if (this.activeId === args.requestId) {
          const request = this.active;
          // cancel 同步解除系统监听；先撤销桥归属，后继请求无需等旧 Promise 的 microtask。
          this.active = null;
          this.activeId = 0;
          request?.cancel();
        }
        return null;
      }
      if (method !== 'currentLocation' || this.active) throw new Error('Location request unavailable');
      const options = new LocationOptions();
      options.timeoutMillis = args.timeoutMillis;
      options.maxAgeMillis = args.maxAgeMillis;
      options.maxAccuracyMeters = args.maxAccuracyMeters;
      const request = this.client.currentLocation(options);
      this.active = request;
      this.activeId = args.requestId;
      request.result.then(result => {
        if (this.destroyed || this.active !== request) return;
        this.active = null;
        this.activeId = 0;
        const fix = result.fix;
        callback?.(result.status === 'available' && fix ? {
          status: result.status, latitude: fix.latitude, longitude: fix.longitude,
          accuracy: fix.accuracyMeters, timestampMillis: fix.timestampMillis
        } : { status: result.status });
      }).catch(() => {
        if (this.destroyed || this.active !== request) return;
        this.active = null;
        this.activeId = 0;
        callback?.({ status: 'error' });
      });
    } catch (_) { callback?.({ status: 'error' }); }
    return null;
  }

  onDestroy(): void {
    this.destroyed = true;
    this.active?.cancel();
    this.active = null;
    this.activeId = 0;
    super.onDestroy();
  }
}
