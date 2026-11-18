-- Premiers jets des personnages jouables et du familier (64×64). Voir generate.lua.
--
-- PLACEHOLDERS : une silhouette commune, une couleur par personnage, une pose par animation, pour que
-- chaque fichier existe avec le bon nom (images/player/<id>.<animation>.<n>.png). Louis les redessine
-- dans art/player/<id>.aseprite ; generate.lua ne touche jamais un tag existant.
-- Personnages tournés vers la droite (l'adversaire est à droite de l'écran).

-- ------------------------------------------------------------------ Personnages

-- look : shirt, dark (ombre du haut), hair, pants. pose : arm ("up" frappe), lean, eyes ("x"), bob, flash.
local function hero(c, look, pose)
  pose = pose or {}
  local b = c:shift(pose.lean or 0, pose.bob or 0)
  -- jambes et chaussures
  b:rect(24, 44, 6, 14, look.pants); b:rect(34, 44, 6, 14, look.pants)
  b:rect(23, 57, 8, 3, "1"); b:rect(33, 57, 9, 3, "1")
  -- torse
  b:rect(21, 27, 22, 18, look.shirt); b:rect(21, 41, 22, 4, look.dark)
  b:vline(32, 28, 44, look.dark)
  -- bras arrière
  b:rect(17, 28, 5, 14, look.dark); b:disc(19, 43, 2, "s")
  -- bras avant : le long du corps, ou tendu vers l'adversaire
  if pose.arm == "up" then
    b:rect(42, 27, 14, 5, look.shirt); b:disc(57, 29, 3, "s")
    b:line(60, 24, 63, 21, "Y"); b:line(60, 34, 63, 37, "Y"); b:hline(61, 63, 29, "Y")
  else
    b:rect(42, 28, 5, 14, look.shirt); b:disc(44, 43, 2, "s")
  end
  -- cou, tête, cheveux
  b:rect(29, 23, 6, 5, "s")
  b:ellipse(32, 15, 9, 9, "s")
  b:ellipse(31, 9, 10, 5, look.hair); b:rect(22, 8, 4, 8, look.hair)
  -- visage (de profil vers la droite)
  if pose.eyes == "x" then
    b:line(34, 13, 37, 16, "1"); b:line(37, 13, 34, 16, "1")
  else
    b:rect(35, 13, 2, 3, "1")
  end
  b:hline(34, 38, 19, pose.eyes == "x" and "d" or "N")
  if pose.flash then
    for _, p in ipairs({ { 6, 10 }, { 10, 4 }, { 4, 20 } }) do c:line(p[1], p[2], p[1] + 4, p[2] + 4, "6") end
  end
end

local function heroSprite(id, look)
  local function frame(pose) return function(lib) return lib.drawn(64, function(c) hero(c, look, pose) end) end end
  return {
    id = id,
    animations = {
      { name = "idle", frames = { frame({}), frame({ bob = 1 }) } },
      { name = "attack", frames = { frame({ lean = 2 }), frame({ lean = 4, arm = "up" }) } },
      { name = "hurt", frames = { frame({ lean = -4, eyes = "x", flash = true }) } },
      { name = "death", frames = {
        frame({ lean = -2, eyes = "x" }),
        function(lib) return lib.squashed(lib.drawn(64, function(c) hero(c, look, { eyes = "x" }) end), 0.35) end,
      } },
    },
  }
end

-- ------------------------------------------------------------------ Sassy (dessin dédié)
-- Un sasquatch : grand corps de fourrure brune, tête en pointe, visage gris aux grands yeux noirs,
-- grosses lèvres, torse gris ; il tient un joint à hauteur du torse, la fumée monte. Tourné vers la droite.
-- pose : bob, alt (fumée et braise de l'autre frame), lean, punch (bras tendu vers l'adversaire),
-- eyes ("x"), mouth ("open"), flash.

local function sassyFur(c, x, y, n, l)
  -- mèches de fourrure : petits traits obliques
  for i = 0, n - 1 do c:line(x + i * 3, y + (i % 2), x + i * 3 + 1, y + (i % 2) + 2, l) end
end

local function sassy(c, pose)
  pose = pose or {}
  local b = c:shift(pose.lean or 0, 0)
  local u = b:shift(0, pose.bob or 0)
  -- jambes courtes et pieds gris
  b:rect(21, 50, 9, 9, "n"); b:rect(35, 50, 9, 9, "n")
  b:vline(29, 51, 58, "N"); b:vline(43, 51, 58, "N")
  b:ellipse(27, 60, 6, 2, "3"); b:ellipse(41, 60, 6, 2, "3")
  b:hline(23, 31, 62, "2"); b:hline(37, 45, 62, "2")
  -- bras arrière : long, pend le long du corps
  u:ellipse(14, 41, 5, 13, "N"); u:ellipse(15, 40, 3, 11, "n")
  u:disc(14, 55, 3, "3"); u:px(13, 57, "2"); u:px(15, 57, "2")
  -- corps massif, épaules rondes
  u:ellipse(32, 40, 16, 15, "n"); u:ellipse(32, 31, 18, 8, "n")
  u:arc(32, 40, 16, 15, 90, 160, "N")
  -- torse gris
  u:ellipse(34, 41, 9, 10, "3"); u:ellipse(32, 38, 4, 4, "4"); u:ellipse(34, 41, 9, 10, "3")
  u:arc(34, 41, 9, 10, 200, 340, "n")
  u:line(30, 44, 33, 45, "2"); u:line(35, 49, 38, 48, "2")
  -- mèches sur les épaules et le ventre
  sassyFur(u, 17, 30, 4, "N"); sassyFur(u, 41, 29, 3, "N"); sassyFur(u, 19, 45, 2, "N")
  u:px(20, 27, "o"); u:px(23, 25, "o"); u:px(44, 26, "o"); u:px(47, 28, "o")
  -- tête en pointe, visage gris (de trois quarts vers la droite)
  local h = u
  h:ellipse(33, 16, 11, 12, "n")
  h:tri(25, 10, 40, 10, 32, 0, "n")
  h:line(31, 1, 30, -1, "n"); h:line(33, 0, 35, -2, "n")
  sassyFur(h, 24, 6, 2, "N"); h:px(28, 4, "o"); h:px(37, 5, "o")
  h:ellipse(36, 18, 7, 8, "3")
  h:hline(30, 42, 12, "N")
  h:hline(31, 41, 13, "2")
  -- yeux : deux grands trous noirs, un reflet
  if pose.eyes == "x" then
    h:line(31, 14, 34, 17, "1"); h:line(34, 14, 31, 17, "1")
    h:line(38, 14, 41, 17, "1"); h:line(41, 14, 38, 17, "1")
  else
    h:ellipse(33, 16, 2, 3, "1"); h:ellipse(39, 16, 2, 3, "1")
    h:px(32, 14, "4"); h:px(38, 14, "4")
  end
  -- nez large
  h:hline(36, 39, 20, "2"); h:px(37, 19, "4")
  -- grosses lèvres, bouche fermée ou ouverte
  h:ellipse(37, 24, 6, 3, "2"); h:ellipse(37, 24, 5, 2, "4"); h:hline(34, 40, 22, "5")
  if pose.mouth == "open" then
    h:ellipse(37, 24, 3, 1, "1")
  else
    h:hline(32, 42, 24, "1")
  end
  -- bras avant
  if pose.punch then
    u:ellipse(46, 33, 5, 5, "n")
    u:rect(46, 30, 12, 7, "n"); sassyFur(u, 47, 29, 3, "N")
    u:disc(59, 33, 4, "3"); u:vline(60, 31, 35, "2")
    for _, y in ipairs({ 27, 33, 39 }) do u:hline(1, 7, y, "6") end
  else
    u:ellipse(48, 37, 5, 9, "n"); u:arc(48, 37, 5, 9, 270, 450, "N")
    u:line(49, 45, 41, 44, "n", 5)
    u:disc(40, 45, 4, "2"); u:disc(40, 45, 3, "3"); u:hline(37, 41, 46, "2"); u:px(38, 44, "4")
    -- joint et fumée
    u:hline(42, 47, 43, "6"); u:px(48, 43, pose.alt and "Y" or "R")
    local s = pose.alt and 1 or 0
    u:px(48, 41, "4"); u:px(49 - s, 39, "4"); u:px(48 + s, 37, "4"); u:px(47 + s, 35, "4")
    u:px(48 - s, 33, "5"); u:px(50 - s, 31, "5"); u:px(51, 29 - s, "5"); u:px(50 + s, 26, "5")
  end
  if pose.flash then
    for _, p in ipairs({ { 4, 8 }, { 9, 2 }, { 2, 18 } }) do c:line(p[1], p[2], p[1] + 4, p[2] + 4, "6") end
  end
end

local function sassyFrame(pose) return function(lib) return lib.drawn(64, function(c) sassy(c, pose) end) end end

local SASSY = {
  id = "sassy",
  animations = {
    { name = "idle", frames = { sassyFrame({}), sassyFrame({ bob = 1, alt = true }) } },
    { name = "attack", frames = { sassyFrame({ lean = 2, mouth = "open" }), sassyFrame({ lean = 3, punch = true, mouth = "open" }) } },
    { name = "hurt", frames = { sassyFrame({ lean = -4, eyes = "x", mouth = "open", flash = true }) } },
    { name = "death", frames = {
      sassyFrame({ lean = -2, eyes = "x", mouth = "open" }),
      function(lib) return lib.squashed(lib.drawn(64, function(c) sassy(c, { eyes = "x", mouth = "open" }) end), 0.35) end,
    } },
  },
}

-- ------------------------------------------------------------------ Alien de Sassy (familier)
-- Petit alien vert, antennes, grands yeux noirs. pose : beam (il tire), joint (il fume), ko (assommé), bob.

local function alien(c, pose)
  pose = pose or {}
  local b = c:shift(0, pose.bob or 0)
  if pose.ko then
    -- allongé, yeux en croix, étoiles
    b:ellipse(32, 52, 18, 6, "G"); b:ellipse(32, 52, 18, 6, "G")
    b:ellipse(20, 46, 9, 8, "G"); b:hline(14, 50, 58, "g")
    b:line(16, 43, 19, 46, "1"); b:line(19, 43, 16, 46, "1")
    b:line(22, 43, 25, 46, "1"); b:line(25, 43, 22, 46, "1")
    for _, p in ipairs({ { 14, 32 }, { 22, 28 }, { 30, 32 } }) do b:px(p[1], p[2], "Y"); b:px(p[1] + 1, p[2], "Y"); b:px(p[1], p[2] + 1, "Y") end
    return
  end
  -- antennes
  b:line(25, 14, 21, 6, "g"); b:disc(21, 5, 2, "C")
  b:line(39, 14, 43, 6, "g"); b:disc(43, 5, 2, "C")
  -- tête et yeux
  b:ellipse(32, 22, 13, 11, "G"); b:ellipse(32, 25, 12, 8, "G")
  b:ellipse(27, 22, 4, 5, "1"); b:ellipse(38, 22, 4, 5, "1")
  b:px(26, 20, "6"); b:px(37, 20, "6")
  b:hline(29, 35, 30, "g")
  -- corps, bras, jambes
  b:ellipse(32, 44, 9, 10, "G"); b:ellipse(32, 46, 6, 6, "g")
  b:rect(27, 53, 4, 8, "g"); b:rect(34, 53, 4, 8, "g")
  b:rect(20, 40, 5, 3, "G")
  if pose.beam then
    b:rect(40, 38, 9, 3, "G"); b:rect(48, 37, 3, 5, "3")
    b:hline(51, 63, 39, "C"); b:hline(51, 63, 38, "c"); b:hline(51, 63, 40, "c")
  else
    b:rect(40, 40, 5, 3, "G")
  end
  if pose.joint then
    b:hline(35, 42, 30, "6"); b:px(43, 30, pose.alt and "Y" or "R")
    local y = pose.alt and 20 or 24
    b:px(45, y, "4"); b:px(46, y - 2, "4"); b:px(45, y - 4, "4"); b:px(47, y - 6, "4"); b:px(46, y - 8, "4")
  end
end

local function alienFrame(pose) return function(lib) return lib.drawn(64, function(c) alien(c, pose) end) end end

return {
  heroSprite("louis", { shirt = "R", dark = "r", hair = "N", pants = "u" }),
  heroSprite("dave", { shirt = "B", dark = "b", hair = "n", pants = "2" }),
  SASSY,
  heroSprite("frederic-blc", { shirt = "4", dark = "3", hair = "y", pants = "2" }),
  {
    id = "alien",
    animations = {
      { name = "idle", frames = { alienFrame({}), alienFrame({ bob = 1 }) } },
      { name = "attack", frames = { alienFrame({ beam = false, bob = -1 }), alienFrame({ beam = true }) } },
      { name = "smoke", frames = { alienFrame({ joint = true }), alienFrame({ joint = true, alt = true }) } },
      { name = "ko", frames = { alienFrame({ ko = true }) } },
    },
  },
}
