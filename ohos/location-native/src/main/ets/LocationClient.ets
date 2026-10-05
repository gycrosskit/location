import { abilityAccessCtrl } from '@kit.AbilityKit';
import { geoLocationManager } from '@kit.LocationKit';
import { BusinessError } from '@kit.BasicServicesKit';

/** 单次定位验收条件；由 currentLocation 在启动系统监听前校验。 */
export class LocationOptions {
  /** 等待期限，毫秒，安全整数 1..2147483647，默认 12000。 */
  timeoutMillis: number = 12000;
  /** 缓存最大年龄，毫秒，非负安全整数，默认 300000。 */
  maxAgeMillis: number = 300000;
  /** 最大水平误差半径，米，有限且非负，默认 500。 */
  maxAccuracyMeters: number = 500;
}
/** 系统原始读数，不做坐标系转换或地理编码。 */
export interface LocationFix {
  /** 纬度，角度，-90..90。 */
  latitude: number;
  /** 经度，角度，-180..180。 */
  longitude: number;
  /** 水平误差半径，米，有限且非负。 */
  accuracyMeters: number;
  /** 定位时间，Unix epoch 毫秒；未来读数拒绝。 */
  timestampMillis: number;
}
/** 单次定位终态；cancelled 表示宿主取消，不代表 SDK 失败。 */
export interface LocationResult {
  /** available 时携带 fix，其余状态不保证坐标。 */
  status: 'available' | 'permission_missing' | 'service_disabled' | 'timed_out' | 'unavailable' | 'cancelled';
  /** 仅成功时存在且符合本次验收条件。 */
  fix?: LocationFix;
}
/** 宿主拥有的单次请求句柄；取消立即解除本次监听。 */
export interface LocationRequest {
  /** 完成、超时或取消时结算一次；系统错误映射为 LocationResult。 */
  result: Promise<LocationResult>;
  /** 幂等取消，结果为 cancelled，不影响其他请求。 */
  cancel: () => void;
}

/** 一个请求独占一个监听，宿主销毁时调用 cancel()。不弹出权限申请框。 */
export class LocationClient {
  private readonly access = abilityAccessCtrl.createAtManager();
  private cached: LocationFix | undefined;

  /**
   * UIAbility 主线程调用；先读权限/服务，再复用合格缓存或监听新读数。
   * @param options 毫秒/米阈值，默认 LocationOptions；无效输入在启动监听前抛错。
   * @returns 宿主负责在离开页面时取消的独占请求。
   */
  currentLocation(options: LocationOptions = new LocationOptions()): LocationRequest {
    if (!Number.isSafeInteger(options.timeoutMillis) || options.timeoutMillis <= 0 || options.timeoutMillis > 2147483647 ||
      !Number.isSafeInteger(options.maxAgeMillis) || options.maxAgeMillis < 0 ||
      !Number.isFinite(options.maxAccuracyMeters) || options.maxAccuracyMeters < 0) {
      throw new Error('Invalid location options');
    }
    let cancel: () => void = () => {};
    const result = new Promise<LocationResult>((resolve) => {
      let settled = false;
      let timer: number = -1;
      let listening = false;
      const finish = (value: LocationResult): void => {
        if (settled) { return; }
        settled = true;
        if (timer >= 0) { clearTimeout(timer); }
        if (listening) {
          try { geoLocationManager.off('locationChange', listener); } catch (_) {}
        }
        resolve(value);
      };
      const listener = (location: geoLocationManager.Location): void => {
        if (settled) { return; }
        const fix: LocationFix = {
          latitude: location.latitude, longitude: location.longitude,
          accuracyMeters: location.accuracy, timestampMillis: location.timeStamp
        };
        if (this.accepts(fix, options)) {
          this.cached = fix;
          finish({ status: 'available', fix });
        }
      };
      cancel = () => finish({ status: 'cancelled' });
      try {
        if (this.access.getSelfPermissionStatus('ohos.permission.APPROXIMATELY_LOCATION') !== abilityAccessCtrl.PermissionStatus.GRANTED) {
          finish({ status: 'permission_missing' }); return;
        }
        if (!geoLocationManager.isLocationEnabled()) { finish({ status: 'service_disabled' }); return; }
        const cached = this.cached;
        if (cached !== undefined && this.accepts(cached, options)) { finish({ status: 'available', fix: cached }); return; }
        timer = setTimeout(() => {
          try {
            if (this.access.getSelfPermissionStatus('ohos.permission.APPROXIMATELY_LOCATION') !== abilityAccessCtrl.PermissionStatus.GRANTED) {
              finish({ status: 'permission_missing' });
            } else {
              finish({ status: geoLocationManager.isLocationEnabled() ? 'timed_out' : 'service_disabled' });
            }
          } catch (_) { finish({ status: 'unavailable' }); }
        }, options.timeoutMillis);
        listening = true;
        geoLocationManager.on('locationChange', {
          priority: geoLocationManager.LocationRequestPriority.ACCURACY,
          timeInterval: 1, distanceInterval: 0, maxAccuracy: 0
        }, listener);
      } catch (error) {
        const code = (error as BusinessError).code;
        finish({ status: code === 201 ? 'permission_missing' : code === 3301100 ? 'service_disabled' : 'unavailable' });
      }
    });
    return { result, cancel };
  }

  private accepts(fix: LocationFix, options: LocationOptions): boolean {
    const age = Date.now() - fix.timestampMillis;
    return Number.isFinite(fix.latitude) && fix.latitude >= -90 && fix.latitude <= 90 &&
      Number.isFinite(fix.longitude) && fix.longitude >= -180 && fix.longitude <= 180 &&
      Number.isFinite(fix.accuracyMeters) && fix.accuracyMeters >= 0 && fix.accuracyMeters <= options.maxAccuracyMeters &&
      fix.timestampMillis >= 0 && Number.isFinite(age) && age >= 0 && age <= options.maxAgeMillis;
  }
}
