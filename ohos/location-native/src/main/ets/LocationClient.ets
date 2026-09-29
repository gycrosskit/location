import { abilityAccessCtrl } from '@kit.AbilityKit';
import { geoLocationManager } from '@kit.LocationKit';
import { BusinessError } from '@kit.BasicServicesKit';

export class LocationOptions {
  timeoutMillis: number = 12000;
  maxAgeMillis: number = 300000;
  maxAccuracyMeters: number = 500;
}
export interface LocationFix {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
  timestampMillis: number;
}
export interface LocationResult {
  status: 'available' | 'permission_missing' | 'service_disabled' | 'timed_out' | 'unavailable' | 'cancelled';
  fix?: LocationFix;
}
export interface LocationRequest {
  result: Promise<LocationResult>;
  cancel: () => void;
}

/** 一个请求独占一个监听，宿主销毁时调用 cancel()。不弹出权限申请框。 */
export class LocationClient {
  private readonly access = abilityAccessCtrl.createAtManager();
  private cached: LocationFix | undefined;

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
      Number.isFinite(age) && age >= 0 && age <= options.maxAgeMillis;
  }
}
