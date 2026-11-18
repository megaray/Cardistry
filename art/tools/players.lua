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

-- ------------------------------------------------------------------ Motifs (camouflage, rayures)
-- Toile qui garde les formes de `base` mais choisit la couleur de chaque pixel avec pick(x, y) :
-- le motif suit le corps (coordonnées avant décalage), la lettre passée aux formes est ignorée.

local function patterned(base, pick)
  local p = {}
  for k, f in pairs(base) do
    if type(f) == "function" then p[k] = f end
  end
  p.px = function(_, x, y) base:px(x, y, pick(math.floor(x + 0.5), math.floor(y + 0.5))) end
  return p
end

local function hash(a, b, seed) return (a * 92821 + b * 68917 + seed * 131 + a * b * 7) % 97 end

-- Camouflage en taches : base, puis deux grilles décalées de taches (sombres, claires).
local function camo(base, colors, seed)
  return patterned(base, function(x, y)
    local v1 = hash(math.floor(x / 4), math.floor(y / 3), seed) % 6
    local v2 = hash(math.floor((x + 2) / 3), math.floor((y + 1) / 4), seed + 7) % 7
    if v1 == 0 then return colors[2] end
    if v1 == 1 then return colors[3] end
    if v2 == 0 then return colors[4] end
    if v2 == 1 then return colors[5] or colors[1] end
    return colors[1]
  end)
end

-- ------------------------------------------------------------------ Dave (dessin dédié)
-- Soldat suisse : béret bordeaux, cheveux courts et barbe brune, veste et pantalon de camouflage,
-- rangers noires, fusil d'assaut tenu en travers du torse. Tourné vers la droite.
-- pose : bob, lean, aim (fusil épaulé vers l'adversaire), fire (flamme au canon), eyes ("x"), flash.

local JACKET = { "v", "n", "N", "g", "v" }
local PANTS = { "n", "N", "v", "y", "N" }

local function daveRifleAcross(c)
  -- crosse en bas à gauche, canon en haut à droite
  c:line(20, 47, 25, 42, "1", 3)
  c:line(25, 41, 40, 26, "1", 2); c:line(26, 40, 40, 26, "2")
  c:line(33, 34, 37, 38, "1", 2)               -- chargeur
  c:line(28, 39, 30, 42, "1", 2)               -- poignée
  c:line(36, 27, 39, 24, "2", 2)               -- garde-main
  c:line(41, 25, 48, 17, "1"); c:line(42, 25, 48, 18, "1")
  c:px(47, 16, "2")
end

local function daveRifleAimed(c)
  c:rect(25, 29, 8, 4, "1"); c:hline(25, 32, 29, "2")   -- crosse à l'épaule
  c:rect(33, 28, 15, 4, "1"); c:hline(33, 47, 28, "2")  -- carcasse
  c:rect(44, 29, 7, 3, "2"); c:hline(44, 50, 29, "3")   -- garde-main
  c:hline(51, 59, 30, "1"); c:hline(51, 58, 31, "1")    -- canon
  c:px(57, 29, "1"); c:rect(38, 26, 3, 2, "1")          -- guidon, hausse
  c:line(39, 32, 41, 37, "1", 2)                        -- chargeur
  c:rect(34, 32, 2, 4, "1")                             -- poignée
end

local function dave(c, pose)
  pose = pose or {}
  local b = c:shift(pose.lean or 0, 0)
  local u = b:shift(0, pose.bob or 0)
  local jacket, pants = camo(u, JACKET, 3), camo(b, PANTS, 11)
  -- pantalon de treillis, poche cargo, bas resserré dans les rangers
  pants:rect(23, 44, 8, 13, "n"); pants:rect(33, 44, 8, 13, "n")
  b:vline(31, 46, 55, "N"); b:frame(33, 47, 5, 5, "N")
  -- rangers
  b:rect(22, 55, 9, 4, "1"); b:rect(22, 59, 11, 2, "1"); b:hline(22, 32, 61, "2")
  b:rect(33, 55, 9, 4, "1"); b:rect(33, 59, 11, 2, "1"); b:hline(33, 43, 61, "2")
  b:px(28, 56, "3"); b:px(27, 57, "3"); b:px(39, 56, "3"); b:px(38, 57, "3")
  -- bras arrière (épaule)
  camo(u, { "N", "v", "1", "n" }, 5):rect(17, 28, 6, 12, "v")
  -- veste longue, poches de poitrine, ceinture d'ombre en bas
  jacket:rect(20, 26, 24, 21, "v"); jacket:rect(19, 28, 2, 18, "v")
  u:hline(20, 43, 46, "N")
  u:frame(23, 30, 6, 5, "N"); u:frame(35, 30, 6, 5, "N")
  -- col
  u:tri(27, 25, 32, 25, 29, 29, "N"); u:tri(32, 25, 37, 25, 35, 29, "N")
  -- écusson suisse sur la manche arrière
  u:rect(18, 30, 3, 3, "r"); u:px(19, 31, "6")
  -- fusil et mains
  if pose.aim then
    camo(u, { "N", "v", "1", "n" }, 5):line(21, 30, 34, 34, "v", 4)
    daveRifleAimed(u)
    u:disc(35, 34, 2, "s")
    jacket:line(41, 27, 45, 31, "v", 4)
    u:disc(47, 33, 2, "s")
    if pose.fire then
      u:disc(61, 30, 2, "Y"); u:px(63, 30, "6"); u:px(61, 27, "o"); u:px(61, 33, "o"); u:px(60, 30, "6")
      u:line(59, 27, 63, 25, "Y"); u:line(59, 33, 63, 35, "Y")
    end
  else
    camo(u, { "N", "v", "1", "n" }, 5):line(21, 38, 27, 40, "v", 4)
    daveRifleAcross(u)
    u:disc(29, 40, 2, "s")
    jacket:line(42, 28, 44, 34, "v", 4); jacket:line(44, 34, 39, 30, "v", 4)
    u:disc(38, 29, 2, "s")
  end
  -- cou, tête
  local h = u
  h:rect(29, 21, 6, 6, "s")
  h:ellipse(32, 15, 8, 9, "s")
  -- cheveux courts sous le béret, oreille
  h:rect(24, 10, 3, 9, "N"); h:px(27, 11, "N")
  h:ellipse(27, 16, 2, 3, "s"); h:px(27, 16, "o")
  -- barbe : joues, mâchoire, menton, moustache
  h:ellipse(33, 21, 7, 4, "N"); h:rect(26, 17, 3, 5, "N")
  h:ellipse(34, 20, 4, 2, "s")
  h:hline(35, 39, 19, "N")
  if pose.fire or pose.eyes == "x" then h:hline(35, 38, 21, "1") else h:hline(36, 38, 21, "d") end
  -- œil, sourcil, nez
  if pose.eyes == "x" then
    h:line(34, 12, 37, 15, "1"); h:line(37, 12, 34, 15, "1")
  else
    h:rect(35, 13, 2, 2, "1"); h:hline(34, 37, 11, "N")
  end
  h:px(40, 16, "s"); h:px(40, 17, "o")
  -- béret penché, insigne
  h:ellipse(29, 7, 11, 4, "r"); h:ellipse(25, 5, 6, 3, "r")
  h:hline(22, 40, 10, "d"); h:hline(19, 25, 6, "d"); h:arc(29, 7, 11, 4, 190, 250, "d")
  h:px(37, 8, "Y"); h:px(38, 8, "4")
  if pose.flash then
    for _, p in ipairs({ { 4, 8 }, { 9, 2 }, { 2, 18 } }) do c:line(p[1], p[2], p[1] + 4, p[2] + 4, "6") end
  end
end

local function daveFrame(pose) return function(lib) return lib.drawn(64, function(c) dave(c, pose) end) end end

local DAVE = {
  id = "dave",
  animations = {
    { name = "idle", frames = { daveFrame({}), daveFrame({ bob = 1 }) } },
    { name = "attack", frames = { daveFrame({ lean = 1, aim = true }), daveFrame({ lean = -1, aim = true, fire = true }) } },
    { name = "hurt", frames = { daveFrame({ lean = -4, eyes = "x", flash = true }) } },
    { name = "death", frames = {
      daveFrame({ lean = -2, eyes = "x" }),
      function(lib) return lib.squashed(lib.drawn(64, function(c) dave(c, { eyes = "x" }) end), 0.35) end,
    } },
  },
}

-- ------------------------------------------------------------------ Louis (dessin dédié)
-- Cheveux bruns en bataille (pointes encore décolorées), barbe de quelques jours, pull mexicain
-- à rayures bleues, short rayé gris-vert, baskets noires à semelle blanche, une clope
-- à la main. Tourné vers la droite.
-- pose : bob, alt (fumée et braise de l'autre frame), lean, drag (clope aux lèvres),
-- blow (souffle un nuage de fumée vers l'adversaire), eyes ("x"), flash.

local function bajaStripes(base)
  -- rayures verticales : bleu marine, bandes bleu clair bordées de bleu, un fil blanc de temps en temps
  return patterned(base, function(x)
    local r = x % 6
    if r == 0 or r == 2 then return "b" end
    if r == 1 then return x % 12 == 1 and "5" or "B" end
    return "u"
  end)
end

local function shortsStripes(base)
  return patterned(base, function(_, y) return (y % 3 == 0) and "3" or "4" end)
end

local function louis(c, pose)
  pose = pose or {}
  local b = c:shift(pose.lean or 0, 0)
  local u = b:shift(0, pose.bob or 0)
  local baja = bajaStripes(u)
  -- jambes nues, genoux
  b:rect(25, 50, 5, 7, "s"); b:rect(35, 50, 5, 7, "s")
  b:px(29, 51, "p"); b:px(39, 51, "p")
  -- baskets noires, bande latérale et semelle blanches, lacets
  for _, x in ipairs({ 25, 35 }) do
    -- talon à gauche, bout arrondi vers la droite (dans le sens du regard)
    b:rect(x - 1, 55, 7, 2, "1"); b:rect(x - 1, 57, 11, 3, "1"); b:px(x + 10, 58, "1"); b:px(x + 10, 59, "1")
    b:hline(x - 1, x + 10, 60, "6"); b:px(x + 11, 59, "6")
    b:px(x + 6, 56, "6"); b:px(x + 7, 57, "6"); b:px(x + 5, 55, "6")
    b:line(x, 57, x + 5, 59, "6")
  end
  -- short rayé
  shortsStripes(b):rect(23, 44, 8, 7, "4"); shortsStripes(b):rect(33, 44, 8, 7, "4")
  b:vline(32, 45, 50, "3")
  -- bras arrière
  baja:rect(17, 28, 5, 14, "u"); u:disc(19, 43, 2, "s")
  -- sweat ample, poche kangourou, bord côtelé
  baja:rect(20, 26, 24, 20, "u"); baja:rect(19, 29, 1, 15, "u"); baja:rect(44, 29, 1, 15, "u")
  u:frame(26, 37, 13, 6, "b")
  u:hline(20, 43, 45, "b")
  -- encolure ronde
  u:hline(28, 36, 26, "b")
  -- cou, tête
  local h = u
  h:rect(29, 21, 6, 6, "s")
  h:ellipse(32, 15, 8, 9, "s")
  h:ellipse(27, 16, 2, 3, "s"); h:px(27, 16, "o")
  -- barbe de quelques jours
  for _, p in ipairs({ { 29, 20 }, { 31, 21 }, { 33, 22 }, { 35, 22 }, { 37, 21 }, { 30, 22 }, { 34, 23 }, { 36, 20 }, { 28, 18 } }) do
    h:px(p[1], p[2], "n")
  end
  -- œil mi-clos, sourcil, nez
  if pose.eyes == "x" then
    h:line(34, 12, 37, 15, "1"); h:line(37, 12, 34, 15, "1")
  else
    h:hline(35, 37, 14, "1"); h:hline(35, 37, 13, "s"); h:px(37, 13, "n")
  end
  h:px(40, 15, "s"); h:px(40, 16, "s"); h:px(39, 17, "o")
  -- bouche : fermée, clope aux lèvres, ou souffle
  if pose.blow then
    h:disc(38, 20, 1, "d")
  elseif not pose.drag then
    h:hline(36, 38, 20, "d")
  end
  -- cheveux bruns en bataille, mèches qui partent dans tous les sens
  -- tignasse arrondie : volume en dôme, mèches qui retombent sur la nuque, l'oreille et le front
  h:ellipse(31, 8, 11, 6, "n"); h:ellipse(25, 11, 5, 6, "n"); h:ellipse(37, 9, 6, 4, "n")
  h:disc(21, 7, 2, "n"); h:disc(26, 3, 3, "n"); h:disc(32, 2, 3, "n"); h:disc(38, 4, 3, "n"); h:disc(42, 7, 2, "n")
  h:ellipse(22, 16, 2, 3, "n"); h:ellipse(25, 17, 2, 2, "n")
  h:ellipse(40, 11, 3, 2, "n"); h:px(42, 13, "n")       -- frange sur le front
  h:hline(26, 35, 11, "N"); h:line(23, 12, 25, 17, "N"); h:px(30, 12, "N"); h:px(31, 13, "N"); h:px(39, 13, "N")
  h:arc(31, 8, 11, 6, 100, 170, "N")
  h:line(26, 3, 30, 2, "o"); h:line(33, 3, 37, 4, "o"); h:px(22, 6, "o"); h:px(41, 6, "o")
  h:px(24, 1, "y"); h:px(32, 0, "y"); h:px(39, 2, "y"); h:px(43, 6, "y")
  -- bras avant et clope
  local ember = pose.alt and "Y" or "R"
  if pose.drag then
    -- la main porte la clope à la bouche, la braise rougeoie
    u:line(41, 28, 45, 35, "1", 6); u:line(45, 35, 40, 24, "1", 6)
    baja:line(42, 29, 46, 36, "u", 4); baja:line(46, 36, 41, 25, "u", 4)
    h:disc(41, 23, 2, "s")
    h:hline(38, 43, 20, "6"); h:px(44, 20, "Y"); h:px(44, 19, "o")
  elseif pose.blow then
    u:rect(41, 28, 7, 15, "1"); baja:rect(42, 28, 5, 14, "u"); u:disc(44, 43, 2, "s")
    u:hline(46, 49, 43, "6"); u:px(50, 43, "r")
    -- nuage de fumée soufflé vers l'adversaire
    for _, p in ipairs({ { 43, 20, 2, "5" }, { 48, 19, 3, "4" }, { 54, 20, 4, "4" }, { 59, 19, 4, "3" },
                         { 52, 16, 2, "5" }, { 58, 23, 3, "5" }, { 62, 15, 2, "3" } }) do
      c:shift(pose.lean or 0, pose.bob or 0):disc(p[1], p[2], p[3], p[4])
    end
  else
    -- coude plié, la main à hauteur du menton, la clope entre deux doigts
    u:line(41, 28, 45, 35, "1", 6); u:line(45, 35, 42, 28, "1", 6)
    baja:line(42, 29, 46, 36, "u", 4); baja:line(46, 36, 43, 29, "u", 4)
    h:disc(44, 27, 2, "s"); h:px(46, 26, "s")
    h:hline(46, 50, 25, "6"); h:px(51, 25, ember)
    local s = pose.alt and 1 or 0
    h:px(51, 23, "4"); h:px(52 - s, 21, "4"); h:px(51 + s, 19, "4"); h:px(50 + s, 17, "4")
    h:px(51 - s, 15, "5"); h:px(53 - s, 13, "5"); h:px(54, 11 - s, "5"); h:px(53 + s, 8, "5")
  end
  -- cuffs (poignets rayés) déjà dans le motif ; hurt
  if pose.flash then
    for _, p in ipairs({ { 4, 8 }, { 9, 2 }, { 2, 18 } }) do c:line(p[1], p[2], p[1] + 4, p[2] + 4, "6") end
  end
end

local function louisFrame(pose) return function(lib) return lib.drawn(64, function(c) louis(c, pose) end) end end

local LOUIS = {
  id = "louis",
  animations = {
    { name = "idle", frames = { louisFrame({}), louisFrame({ bob = 1, alt = true }) } },
    { name = "attack", frames = { louisFrame({ lean = 1, drag = true }), louisFrame({ lean = 3, blow = true }) } },
    { name = "hurt", frames = { louisFrame({ lean = -4, eyes = "x", flash = true }) } },
    { name = "death", frames = {
      louisFrame({ lean = -2, eyes = "x" }),
      function(lib) return lib.squashed(lib.drawn(64, function(c) louis(c, { eyes = "x" }) end), 0.35) end,
    } },
  },
}

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
  LOUIS,
  DAVE,
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
