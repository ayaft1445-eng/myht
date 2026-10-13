"""Рисует иконку предмета по .blockymodel и текстуре (ортогональная проекция, z-буфер)."""
import json, math, sys
import numpy as np
from PIL import Image

def qmat(q):
    w, x, y, z = q["w"], q["x"], q["y"], q["z"]
    return np.array([
        [1-2*(y*y+z*z), 2*(x*y-w*z),   2*(x*z+w*y)],
        [2*(x*y+w*z),   1-2*(x*x+z*z), 2*(y*z-w*x)],
        [2*(x*z-w*y),   2*(y*z+w*x),   1-2*(x*x+y*y)]])

def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])

def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])

QUAD_ROT = {"+Z": np.eye(3), "-Z": rot_y(math.pi), "+X": rot_y(math.pi/2), "-X": rot_y(-math.pi/2),
            "+Y": rot_x(-math.pi/2), "-Y": rot_x(math.pi/2)}

def box_faces(sx, sy, sz):
    hx, hy, hz = sx/2, sy/2, sz/2
    # имя: (левый-верхний угол, ось u, ось v, ширина, высота) в пикселях текстуры
    return {
        "front":  ((-hx,  hy,  hz), ( 1, 0, 0), (0, -1, 0), sx, sy),
        "back":   (( hx,  hy, -hz), (-1, 0, 0), (0, -1, 0), sx, sy),
        "left":   ((-hx,  hy, -hz), ( 0, 0, 1), (0, -1, 0), sz, sy),
        "right":  (( hx,  hy,  hz), ( 0, 0,-1), (0, -1, 0), sz, sy),
        "top":    ((-hx,  hy, -hz), ( 1, 0, 0), (0, 0, 1), sx, sz),
        "bottom": ((-hx, -hy,  hz), ( 1, 0, 0), (0, 0, -1), sx, sz),
    }

def collect(model):
    faces = []
    def walk(node, R, T):
        lr = qmat(node["orientation"])
        p = np.array([node["position"][k] for k in "xyz"], float)
        R2 = R @ lr
        T2 = T + R @ p
        s = node["shape"]
        if s["type"] in ("box", "quad") and s.get("visible", True):
            st = np.array([s["stretch"][k] for k in "xyz"], float)
            off = np.array([s["offset"][k] for k in "xyz"], float)
            size = s["settings"]["size"]
            if s["type"] == "box":
                defs = box_faces(size["x"], size["y"], size["z"])
                pre = np.eye(3); cull = True
            else:
                defs = {"front": ((-size["x"]/2, size["y"]/2, 0), (1, 0, 0), (0, -1, 0), size["x"], size["y"])}
                pre = QUAD_ROT.get(s["settings"].get("normal", "+Z"), np.eye(3)); cull = False
            for name, (o, u, v, w, h) in defs.items():
                lay = s["textureLayout"].get(name)
                if lay is None:
                    continue
                def tf(pt):
                    return T2 + R2 @ (off + st * (pre @ np.array(pt, float)))
                O = tf(o)
                U = tf(np.array(o) + np.array(u) * w) - O
                V = tf(np.array(o) + np.array(v) * h) - O
                faces.append((O, U, V, w, h, lay, cull or s.get("doubleSided") is False and False))
        for c in node.get("children", []):
            walk(c, R2, T2)
    for n in model["nodes"]:
        walk(n, np.eye(3), np.zeros(3))
    return faces

def tex_lookup(tex, lay, w, h, fu, fv):
    if lay["mirror"]["x"]: fu = 1 - fu
    if lay["mirror"]["y"]: fv = 1 - fv
    a = int(lay.get("angle", 0)) % 360
    if a == 90:   fu, fv, rw, rh = 1 - fv, fu, h, w
    elif a == 180: fu, fv, rw, rh = 1 - fu, 1 - fv, w, h
    elif a == 270: fu, fv, rw, rh = fv, 1 - fu, h, w
    else: rw, rh = w, h
    tx = lay["offset"]["x"] + np.clip((fu * rw).astype(int), 0, max(int(rw) - 1, 0))
    ty = lay["offset"]["y"] + np.clip((fv * rh).astype(int), 0, max(int(rh) - 1, 0))
    tx = np.clip(tx, 0, tex.shape[1] - 1); ty = np.clip(ty, 0, tex.shape[0] - 1)
    return tex[ty, tx]

def render(model_path, tex_path, out_path, size=64, yaw=-35, pitch=25, ss=4):
    model = json.load(open(model_path))
    tex = np.array(Image.open(tex_path).convert("RGBA"))
    faces = collect(model)
    view = rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw))
    vf = [(view @ O, view @ U, view @ V, w, h, lay) for O, U, V, w, h, lay, _ in faces]
    pts = np.array([p for O, U, V, *_ in vf for p in (O, O+U, O+V, O+U+V)])
    mn, mx = pts[:, :2].min(0), pts[:, :2].max(0)
    N = size * ss
    scale = (N * 0.90) / max(mx - mn)
    center = (mn + mx) / 2
    img = np.zeros((N, N, 4), np.uint8)
    zbuf = np.full((N, N), np.inf)
    light = np.array([-0.4, 0.8, 0.6]); light /= np.linalg.norm(light)
    for O, U, V, w, h, lay in vf:
        def scr(p):  # x вправо, y вниз; глубина = -z (камера смотрит вдоль -Z)
            return np.array([(p[0] - center[0]) * scale + N/2, -(p[1] - center[1]) * scale + N/2])
        o2 = scr(O); u2 = scr(O + U) - o2; v2 = scr(O + V) - o2
        det = u2[0]*v2[1] - u2[1]*v2[0]
        if abs(det) < 1e-6:
            continue
        corners = np.array([o2, o2+u2, o2+v2, o2+u2+v2])
        x0, y0 = np.floor(corners.min(0)).astype(int); x1, y1 = np.ceil(corners.max(0)).astype(int)
        x0, y0 = max(x0, 0), max(y0, 0); x1, y1 = min(x1, N), min(y1, N)
        if x0 >= x1 or y0 >= y1:
            continue
        ys, xs = np.mgrid[y0:y1, x0:x1] + 0.5
        dx, dy = xs - o2[0], ys - o2[1]
        fu = (dx * v2[1] - dy * v2[0]) / det
        fv = (u2[0] * dy - u2[1] * dx) / det
        inside = (fu >= 0) & (fu < 1) & (fv >= 0) & (fv < 1)
        if not inside.any():
            continue
        depth = -(O[2] + fu * U[2] + fv * V[2])
        col = tex_lookup(tex, lay, w, h, np.clip(fu, 0, 0.9999), np.clip(fv, 0, 0.9999))
        n = np.cross(U, V); nn = np.linalg.norm(n)
        shade = 0.62 + 0.38 * abs(float(n @ light) / nn) if nn > 0 else 1
        sub = zbuf[y0:y1, x0:x1]
        m = inside & (col[..., 3] > 8) & (depth < sub)
        sub[m] = depth[m]
        rgb = (col[..., :3].astype(float) * shade).clip(0, 255).astype(np.uint8)
        region = img[y0:y1, x0:x1]
        region[m, :3] = rgb[m]; region[m, 3] = 255
    # Уменьшение без полупрозрачности: цвет — среднее по закрашенным субпикселям,
    # пиксель либо целиком виден (покрыт хотя бы наполовину), либо пуст.
    blk = img.reshape(size, ss, size, ss, 4).astype(float)
    a = blk[..., 3] / 255.0
    cov = a.mean(axis=(1, 3))
    rgb = (blk[..., :3] * a[..., None]).sum(axis=(1, 3)) / np.maximum(a.sum(axis=(1, 3)), 1e-6)[..., None]
    solid = cov >= 0.5
    res = np.zeros((size, size, 4), np.uint8)
    res[solid, :3] = rgb[solid].clip(0, 255).astype(np.uint8)
    res[solid, 3] = 255
    # Тёмный контур в один пиксель, чтобы иконка читалась на любом фоне слота.
    pad = np.pad(solid, 1)
    near = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
    edge = near & ~solid
    res[edge] = (24, 22, 30, 255)
    Image.fromarray(res, "RGBA").save(out_path)

if __name__ == "__main__":
    a = sys.argv
    render(a[1], a[2], a[3], size=int(a[4]) if len(a) > 4 else 64,
           yaw=float(a[5]) if len(a) > 5 else -35, pitch=float(a[6]) if len(a) > 6 else 25)
