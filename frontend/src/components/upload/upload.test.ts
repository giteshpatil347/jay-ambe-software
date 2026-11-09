import { vi } from 'vitest';
import { validateQueued } from './useFileQueue';
import { downscaleImage } from './imageDownscale';

const file = (name: string, type: string, bytes = 10) => new File([new Uint8Array(bytes)], name, { type });

describe('validateQueued', () => {
  it('accepts jpg/png/pdf and rejects other types (webp, exe)', () => {
    expect(validateQueued(file('a.jpg', 'image/jpeg'), 0)).toBeNull();
    expect(validateQueued(file('a.pdf', 'application/pdf'), 0)).toBeNull();
    expect(validateQueued(file('a.webp', 'image/webp'), 0)).toMatch(/not allowed/);
    expect(validateQueued(file('a.exe', 'application/x-msdownload'), 0)).toMatch(/not allowed/);
  });
  it('rejects an oversize PDF and the 11th file, but lets big camera photos through for downscaling', () => {
    expect(validateQueued(file('a.pdf', 'application/pdf', 10 * 1024 * 1024 + 1), 0)).toMatch(/10 MB/);
    expect(validateQueued(file('a.pdf', 'application/pdf'), 10)).toMatch(/Maximum 10/);
    expect(validateQueued(file('IMG.jpg', 'image/jpeg', 12 * 1024 * 1024), 0)).toBeNull();
    expect(validateQueued(file('a.jpg', 'image/jpeg', 0), 0)).toMatch(/empty/);
  });
});

describe('downscaleImage', () => {
  const draw = vi.fn();
  let canvasSize = { w: 0, h: 0 };
  beforeEach(() => {
    draw.mockReset();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ fillRect: vi.fn(), drawImage: draw, fillStyle: '' } as never);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (this: HTMLCanvasElement, cb: BlobCallback, type?: string, q?: unknown) {
      canvasSize = { w: this.width, h: this.height };
      expect(type).toBe('image/jpeg'); expect(q).toBeCloseTo(0.82);
      cb(new Blob([new Uint8Array(1000)], { type: 'image/jpeg' }));
    });
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('shrinks the longest side to 2000 px, honours EXIF orientation, outputs jpg', async () => {
    const close = vi.fn();
    const cib = vi.fn().mockResolvedValue({ width: 4000, height: 3000, close });
    vi.stubGlobal('createImageBitmap', cib);
    const out = await downscaleImage(file('IMG_1.PNG', 'image/png', 5 * 1024 * 1024));
    expect(cib).toHaveBeenCalledWith(expect.any(File), { imageOrientation: 'from-image' });
    expect(canvasSize).toEqual({ w: 2000, h: 1500 });
    expect(out.type).toBe('image/jpeg'); expect(out.name).toBe('IMG_1.jpg'); expect(out.size).toBe(1000);
    expect(close).toHaveBeenCalled();
  });
  it('leaves PDFs untouched and falls back to the original when decoding fails', async () => {
    const cib = vi.fn().mockRejectedValue(new Error('decode'));
    vi.stubGlobal('createImageBitmap', cib);
    const pdf = file('a.pdf', 'application/pdf');
    expect(await downscaleImage(pdf)).toBe(pdf);
    expect(cib).not.toHaveBeenCalled();
    const img = file('a.jpg', 'image/jpeg');
    expect(await downscaleImage(img)).toBe(img);
  });
  it('keeps small images as they are', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width: 800, height: 600, close: vi.fn() }));
    const img = file('s.jpg', 'image/jpeg', 100 * 1024);
    expect(await downscaleImage(img)).toBe(img);
  });
});
