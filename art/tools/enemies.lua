-- Sprites d'adversaires, 64×64, tournés vers la gauche (le joueur est à gauche de l'écran).
-- Chaque adversaire : des animations (idle, attack, hurt, death) = des tags dans
-- art/enemies/<id>.aseprite. generate.lua n'ajoute que les animations dont le tag est absent.
-- Ébauches dessinées par formes ; Clarence n'est pas ici (dessiné à la main).

-- ------------------------------------------------------------------ Caddie énervé

local function caddieLeft(y) return math.floor(6 + (y - 16) * 6 / 24 + 0.5) end
local function caddieRight(y) return math.floor(52 - (y - 16) * 4 / 24 + 0.5) end

local function wheel(c, x, y)
  c:disc(x, y, 4, "3"); c:disc(x, y, 2, "2"); c:px(x, y, "6")
  c:px(x - 3, y - 3, "4")
end

-- pose : bob (le panier descend), eyes ("angry", "wide", "x"), mouth ("teeth", "open"), speed
local function caddie(c, pose)
  pose = pose or {}
  local bob = pose.bob or 0
  local b = c:shift(0, bob)
  -- châssis et roues (ne bougent pas)
  c:line(14, 41 + bob, 12, 52, "4"); c:line(46, 41 + bob, 50, 52, "4")
  c:hline(12, 50, 48, "4"); c:hline(12, 50, 49, "3")
  wheel(c, 12, 57); wheel(c, 50, 57)
  -- panier : fond, grillage, bords
  for y = 16, 40 do b:hline(caddieLeft(y), caddieRight(y), y, "1") end
  for i = 0, 6 do b:line(6 + i * 46 / 6, 16, 12 + i * 6, 40, "3") end
  for _, y in ipairs({ 22, 28, 34 }) do b:hline(caddieLeft(y), caddieRight(y), y, "3") end
  b:line(6, 16, 12, 40, "4"); b:line(52, 16, 48, 40, "4")
  b:hline(12, 48, 40, "4"); b:hline(13, 47, 41, "3")
  b:hline(5, 53, 15, "6"); b:hline(5, 53, 16, "4")
  -- poignée
  b:line(53, 14, 59, 8, "4", 2); b:rect(57, 5, 6, 3, "R"); b:rect(57, 7, 6, 1, "r")
  -- yeux
  local eyes = pose.eyes or "angry"
  for _, x in ipairs({ 16, 33 }) do
    if eyes == "x" then
      b:rect(x, 22, 7, 6, "1")
      b:line(x, 22, x + 5, 27, "R", 2); b:line(x + 5, 22, x, 27, "R", 2)
    else
      local top = eyes == "wide" and 20 or 22
      b:rect(x, top, 7, 28 - top, "Y"); b:rect(x, 27, 7, 1, "y")
      b:rect(x + 1, 24, 3, eyes == "wide" and 3 or 4, "1")
    end
  end
  if eyes ~= "x" then
    b:line(13, 18, 24, 22, "R", 2); b:line(42, 18, 31, 22, "R", 2)
  end
  -- gueule
  if pose.mouth == "open" then
    b:rect(17, 30, 23, 8, "d"); b:rect(18, 31, 21, 6, "1")
    for x = 18, 36, 4 do b:rect(x, 31, 2, 2, "6"); b:rect(x + 2, 35, 2, 2, "6") end
  else
    for x = 17, 37, 4 do b:line(x, 32, x + 2, 35, "6"); b:line(x + 2, 35, x + 4, 32, "6") end
  end
  if pose.speed then
    for _, y in ipairs({ 20, 27, 34 }) do c:hline(57, 63, y + bob, "R"); c:hline(60, 63, y + 2 + bob, "r") end
  end
end

local caddieEnerve = {
  id = "caddie-enerve",
  animations = {
    { name = "idle", frames = {
      function(lib) return lib.drawn(64, function(c) caddie(c) end) end,
      function(lib) return lib.drawn(64, function(c) caddie(c, { bob = 1 }) end) end,
    } },
    { name = "attack", frames = {
      function(lib) return lib.drawn(64, function(c) caddie(c:shift(-3, 0), { eyes = "wide", speed = true }) end) end,
      function(lib) return lib.drawn(64, function(c)
        caddie(c:shift(-6, 0), { eyes = "wide", mouth = "open", speed = true })
      end) end,
    } },
    { name = "hurt", frames = {
      function(lib) return lib.drawn(64, function(c)
        caddie(c:shift(3, 0), { eyes = "x" })
        c:line(2, 8, 6, 12, "6"); c:line(6, 8, 2, 12, "6"); c:px(4, 6, "Y"); c:px(4, 14, "Y")
      end) end,
    } },
    { name = "death", frames = {
      function(lib) return lib.squashed(lib.drawn(64, function(c) caddie(c, { eyes = "x", bob = 2 }) end), 0.7) end,
      function(lib) return lib.squashed(lib.drawn(64, function(c) caddie(c, { eyes = "x", bob = 3 }) end), 0.38) end,
    } },
  },
}

-- ------------------------------------------------------------------ Seau de fuite

local function seauLeft(y) return math.floor(14 + (y - 20) * 6 / 32 + 0.5) end
local function seauRight(y) return math.floor(50 - (y - 20) * 6 / 32 + 0.5) end

-- pose : wave (clapotis), leak (variante des gouttes), eyes ("worried", "x", "wide"), mouth ("flat", "open"),
--        splash (0, 1, 2 : vague projetée vers la gauche), puddle (largeur de la flaque)
local function seau(c, pose)
  pose = pose or {}
  local wave = pose.wave or 0
  -- anse
  c:arc(32, 20, 21, 15, 180, 360, "3"); c:arc(32, 21, 21, 15, 180, 360, "2")
  -- flaque et ombre
  local puddle = pose.puddle or 8
  c:ellipse(10, 60, puddle, 2, "c"); c:hline(10 - puddle + 3, 10 + puddle - 4, 59, "C")
  c:hline(21, 46, 54, "2")
  -- corps
  for y = 20, 52 do
    local xl, xr = seauLeft(y), seauRight(y)
    c:hline(xl, xr, y, "b")
    c:hline(xl + 1, xl + 3, y, "B")
    c:hline(xr - 4, xr, y, "u")
    c:px(xl, y, "u")
  end
  c:hline(seauLeft(52) + 1, seauRight(52) - 1, 53, "u")
  for _, y in ipairs({ 31, 45 }) do
    c:hline(seauLeft(y), seauRight(y), y, "u"); c:hline(seauLeft(y) + 1, seauLeft(y) + 3, y, "b")
  end
  -- bord et eau
  c:rect(12, 17, 41, 4, "4"); c:hline(12, 52, 17, "6"); c:hline(12, 52, 20, "3")
  c:rect(15, 18, 35, 2, "C")
  c:hline(17 + wave, 23 + wave, 18, "6"); c:hline(33 - wave, 40 - wave, 19, "c")
  -- visage
  local eyes = pose.eyes or "worried"
  for _, x in ipairs({ 22, 36 }) do
    if eyes == "x" then
      c:line(x, 27, x + 5, 32, "1", 2); c:line(x + 5, 27, x, 32, "1", 2)
    else
      c:rect(x, 27, 6, 7, "6"); c:rect(x, 33, 6, 1, "5")
      c:rect(x + 1, eyes == "wide" and 28 or 30, 3, 3, "1")
    end
  end
  if eyes ~= "x" then
    c:line(21, 25, 27, 23, "u", 1); c:line(42, 25, 36, 23, "u", 1)
  end
  if pose.mouth == "open" then
    c:ellipse(32, 41, 4, 3, "1"); c:hline(30, 34, 43, "d")
  else
    c:hline(28, 36, 40, "1"); c:px(27, 41, "1"); c:px(37, 41, "1")
  end
  -- fuite : un filet d'eau sort d'un trou à gauche
  c:rect(seauLeft(42), 41, 2, 3, "1")
  local leak = pose.leak or 0
  local jet = { { 16, 42 }, { 14, 43 }, { 12, 45 }, { 11, 47 }, { 10, 50 }, { 9, 53 }, { 9, 56 } }
  for i, p in ipairs(jet) do
    if (i + leak) % 4 ~= 0 then c:rect(p[1], p[2], 2, 2, "C"); c:px(p[1], p[2], "6") end
  end
  c:px(6 + leak * 2, 55 + leak, "C"); c:px(14 - leak, 57, "C")
  -- vague projetée (attaque)
  local splash = pose.splash or 0
  if splash > 0 then
    local reach = splash * 6
    c:tri(14, 19, 14 - reach, 6, 20, 12, "C")
    c:ellipse(12 - reach, 8, 3 + splash, 3, "C"); c:ellipse(11 - reach, 7, 2 + splash, 2, "6")
    c:ellipse(14 - reach, 9, 2, 2, "B")
    c:px(6 - reach, 14, "C"); c:px(18 - reach, 3, "C"); c:px(9 - reach, 17, "B")
    if splash > 1 then
      c:rect(2, 20, 2, 2, "C"); c:rect(5, 26, 2, 2, "C"); c:px(1, 31, "B")
    end
  end
end

local seauDeFuite = {
  id = "seau-de-fuite",
  animations = {
    { name = "idle", frames = {
      function(lib) return lib.drawn(64, function(c) seau(c) end) end,
      function(lib) return lib.drawn(64, function(c) seau(c, { wave = 2, leak = 1 }) end) end,
    } },
    { name = "attack", frames = {
      function(lib) return lib.drawn(64, function(c)
        seau(c:shift(-1, 0), { eyes = "wide", mouth = "open", splash = 1, wave = 3 })
      end) end,
      function(lib) return lib.drawn(64, function(c)
        seau(c:shift(-2, 0), { eyes = "wide", mouth = "open", splash = 2, wave = 1, leak = 1 })
      end) end,
    } },
    { name = "hurt", frames = {
      function(lib) return lib.drawn(64, function(c)
        seau(c:shift(3, 0), { eyes = "x", mouth = "open", wave = 3 })
        c:rect(22, 9, 2, 2, "C"); c:rect(33, 5, 2, 2, "C"); c:rect(44, 8, 2, 2, "C"); c:px(28, 12, "6")
      end) end,
    } },
    { name = "death", frames = {
      function(lib) return lib.squashed(lib.drawn(64, function(c) seau(c, { eyes = "x", puddle = 9 }) end), 0.7) end,
      function(lib) return lib.squashed(lib.drawn(64, function(c) seau(c, { eyes = "x", puddle = 9 }) end), 0.4) end,
    } },
  },
}

-- ------------------------------------------------------------------ outils communs

local function xEye(c, x, y, size, color)
  c:line(x, y, x + size, y + size, color, 2); c:line(x + size, y, x, y + size, color, 2)
end

local function impact(c)
  c:line(2, 8, 6, 12, "6"); c:line(6, 8, 2, 12, "6"); c:px(4, 6, "Y"); c:px(4, 14, "Y")
end

-- Animations habituelles à partir d'un seul dessin draw(canvas, pose) :
--   idle   : pose {} puis { alt = true }
--   attack : { attack = 1 } décalé de 3 px vers le joueur, puis { attack = 2 } décalé de 6 px
--   hurt   : { hurt = true } en recul, avec un éclat
--   death  : la pose hurt écrasée vers le sol, deux fois
local function standard(id, draw)
  local function frame(dx, pose, extra)
    return function(lib)
      return lib.drawn(64, function(c)
        draw(c:shift(dx, 0), pose)
        if extra then extra(c) end
      end)
    end
  end
  local function fallen(factor)
    return function(lib)
      return lib.squashed(lib.drawn(64, function(c) draw(c, { hurt = true, dead = true }) end), factor)
    end
  end
  return { id = id, animations = {
    { name = "idle", frames = { frame(0, {}), frame(0, { alt = true }) } },
    { name = "attack", frames = { frame(-3, { attack = 1 }), frame(-6, { attack = 2 }) } },
    { name = "hurt", frames = { frame(3, { hurt = true }, impact) } },
    { name = "death", frames = { fallen(0.7), fallen(0.4) } },
  } }
end

-- ------------------------------------------------------------------ Camé (feu)
-- Capuche rouge, yeux rouges mi-clos, clope au bec ; à l'attaque il tend la main (la Taxe).

local function came(c, pose)
  local bob = pose.alt and 1 or 0
  -- corps
  c:ellipse(32, 46, 19, 9, "r"); c:rect(14, 46, 37, 17, "r")
  c:rect(44, 44, 7, 19, "d"); c:rect(24, 53, 16, 6, "d"); c:hline(24, 39, 53, "1")
  c:vline(29, 42, 49, "6"); c:vline(35, 42, 49, "6")
  -- bras tendu (attaque)
  if pose.attack then
    local reach = pose.attack * 4
    c:rect(14 - reach, 43, reach + 6, 6, "r"); c:hline(14 - reach, 19, 48, "d")
    c:disc(12 - reach, 45, 3, "s"); c:px(9 - reach, 43, "s"); c:px(9 - reach, 47, "s")
  end
  local h = c:shift(0, bob)
  -- capuche et visage
  h:disc(32, 24, 17, "r"); h:arc(32, 24, 17, 17, 300, 420, "d"); h:arc(32, 24, 16, 16, 300, 420, "d")
  h:ellipse(31, 27, 11, 12, "s"); h:arc(31, 27, 11, 12, 180, 360, "n")
  h:rect(25, 15, 13, 3, "N")
  -- yeux
  for _, x in ipairs({ 23, 33 }) do
    if pose.hurt then
      xEye(h, x, 22, 4, "1")
    else
      h:rect(x, 23, 6, 4, "6"); h:rect(x + 1, 24, 3, 3, "R"); h:px(x + 2, 25, "1")
      h:hline(x, x + 5, 22, "n"); h:hline(x, x + 5, 23, pose.attack and "6" or "n")
      h:hline(x, x + 5, 28, "p")
    end
  end
  h:rect(30, 29, 2, 3, "n")
  -- bouche et clope
  if pose.attack == 2 or pose.hurt then
    h:ellipse(31, 35, 3, 2, "1")
  else
    h:hline(27, 35, 35, "N"); h:px(36, 34, "N")
  end
  if not pose.dead then
    h:rect(17, 34, 10, 2, "6"); h:rect(16, 34, 2, 2, "o"); h:px(15, 34, "R")
    local drift = pose.alt and 1 or 0
    h:px(14 - drift, 31, "4"); h:px(13 + drift, 28, "4"); h:px(15 - drift, 25, "3"); h:px(13 + drift, 22, "3")
  end
end

-- ------------------------------------------------------------------ Paresseux (plante)
-- Accroché à sa branche ; « Badoing » : il se balance. À l'attaque, un bras lâche et griffe.

local function paresseux(c, pose)
  local sag = pose.alt and 2 or 0
  -- branche et feuilles (ne bougent pas)
  local branch = pose.dead and c or c:shift(-c.dx, 0)
  branch:rect(0, 6, 64, 4, "n"); branch:hline(0, 63, 10, "N"); branch:hline(0, 63, 6, "y")
  branch:ellipse(6, 4, 5, 2, "G"); branch:ellipse(55, 3, 6, 2, "g"); branch:ellipse(58, 11, 4, 2, "G")
  local b = c:shift(0, sag)
  -- bras accrochés
  c:rect(41, 10, 6, 22 + sag, "n"); c:vline(46, 10, 30 + sag, "N")
  c:rect(40, 7, 2, 4, "5"); c:rect(43, 7, 2, 4, "5"); c:rect(46, 7, 2, 4, "5")
  if pose.attack then
    local reach = pose.attack * 5
    b:line(22, 30, 14 - reach, 36, "n", 5)
    for i = 0, 2 do b:line(13 - reach, 36 + i * 2, 8 - reach, 38 + i * 3, "6") end
  else
    c:rect(17, 10, 6, 22 + sag, "n"); c:vline(17, 10, 30 + sag, "N")
    c:rect(16, 7, 2, 4, "5"); c:rect(19, 7, 2, 4, "5"); c:rect(22, 7, 2, 4, "5")
  end
  -- corps
  b:ellipse(32, 40, 16, 15, "n"); b:ellipse(36, 42, 11, 11, "N"); b:ellipse(31, 43, 9, 9, "n")
  b:ellipse(30, 45, 7, 6, "y")
  -- pattes
  b:rect(22, 52, 6, 8, "n"); b:rect(37, 52, 6, 8, "n")
  b:rect(21, 59, 2, 3, "5"); b:rect(24, 59, 2, 3, "5"); b:rect(27, 59, 2, 3, "5")
  b:rect(36, 59, 2, 3, "5"); b:rect(39, 59, 2, 3, "5"); b:rect(42, 59, 2, 3, "5")
  -- tête
  b:ellipse(30, 29, 11, 9, "n"); b:ellipse(30, 30, 9, 7, "5")
  b:ellipse(25, 29, 3, 2, "N"); b:ellipse(35, 29, 3, 2, "N")
  if pose.hurt then
    xEye(b, 23, 27, 3, "1"); xEye(b, 33, 27, 3, "1")
  elseif pose.attack then
    b:rect(24, 28, 2, 2, "6"); b:rect(34, 28, 2, 2, "6")
  else
    b:hline(23, 26, 29, "1"); b:hline(33, 36, 29, "1")
  end
  b:rect(29, 31, 3, 2, "1")
  b:arc(30, 32, 4, 3, 20, 160, "N")
end

-- ------------------------------------------------------------------ Le Peneu (neutre)
-- Un gros pneu qui rebondit, le visage dans la jante.

local function peneu(c, pose)
  local lift = pose.alt and 3 or 0
  c:ellipse(32, 61, 18 - lift, 2, "2")
  local t = c:shift(0, -lift)
  t:disc(32, 33, 26, "3"); t:disc(32, 33, 24, "2")
  for a = 0, 340, 20 do
    local rad = math.rad(a + (pose.attack and pose.attack * 10 or 0))
    t:rect(32 + 25 * math.cos(rad) - 1, 33 + 25 * math.sin(rad) - 1, 3, 3, "1")
  end
  t:arc(32, 33, 20, 20, 200, 290, "3")
  t:disc(32, 33, 15, "3"); t:disc(32, 33, 14, "4"); t:disc(32, 33, 12, "5")
  t:arc(32, 33, 13, 13, 20, 110, "3")
  for a = 30, 330, 60 do
    local rad = math.rad(a)
    t:disc(32 + 9 * math.cos(rad), 33 + 9 * math.sin(rad), 1, "3")
  end
  -- visage
  if pose.hurt then
    xEye(t, 25, 28, 4, "1"); xEye(t, 35, 28, 4, "1")
    t:ellipse(32, 39, 3, 2, "1")
  else
    t:rect(25, 28, 4, 5, "1"); t:rect(35, 28, 4, 5, "1"); t:px(25, 28, "6"); t:px(35, 28, "6")
    t:line(23, 25, 29, 27, "1", 2); t:line(41, 25, 35, 27, "1", 2)
    if pose.attack then
      t:rect(27, 37, 11, 4, "1"); t:hline(28, 36, 37, "6")
    else
      t:hline(28, 36, 39, "1"); t:px(27, 38, "1"); t:px(37, 38, "1")
    end
  end
  if pose.attack then
    for _, y in ipairs({ 18, 30, 42 }) do c:hline(58, 63, y, "4"); c:hline(60, 63, y + 2, "3") end
  end
end

-- ------------------------------------------------------------------ T-Rex défoncé (plante)
-- De profil, tourné vers la gauche : œil rouge mi-clos, bras trop courts, un joint au coin de la gueule.

local function trex(c, pose)
  local open = (pose.attack == 2 or pose.hurt) and 4 or (pose.attack and 2 or 0)
  -- queue, corps, pattes
  c:tri(46, 34, 63, 52, 48, 50, "g"); c:tri(50, 42, 63, 52, 50, 50, "v")
  c:ellipse(38, 40, 14, 13, "g"); c:ellipse(34, 44, 8, 9, "G")
  c:rect(31, 50, 7, 10, "g"); c:rect(42, 50, 7, 10, "g"); c:vline(48, 50, 59, "v")
  c:rect(27, 59, 11, 3, "v"); c:rect(38, 59, 11, 3, "v")
  for _, x in ipairs({ 27, 30, 38, 41 }) do c:px(x, 62, "6") end
  for _, p in ipairs({ { 42, 30 }, { 47, 35 }, { 40, 36 }, { 45, 42 } }) do c:rect(p[1], p[2], 2, 2, "v") end
  -- petits bras
  local arm = pose.alt and 1 or 0
  c:rect(23, 38 + arm, 7, 2, "v"); c:px(22, 39 + arm, "6"); c:px(22, 37 + arm, "6")
  c:rect(25, 43 - arm, 6, 2, "v"); c:px(24, 44 - arm, "6")
  -- cou et tête
  c:tri(22, 20, 36, 20, 34, 36, "g"); c:rect(24, 22, 10, 12, "g")
  c:ellipse(22, 17, 13, 8, "g"); c:ellipse(11, 19, 9, 5, "g")
  c:hline(4, 20, 14, "G"); c:px(4, 17, "v"); c:px(6, 16, "v")
  -- mâchoire
  c:rect(4, 23 + open, 20, 4, "g"); c:hline(4, 23, 26 + open, "v")
  if open > 0 then c:rect(5, 23, 18, open, "d") end
  for x = 5, 21, 3 do c:px(x, 23, "6"); c:px(x + 1, 22 + open, "6") end
  -- œil
  if pose.hurt then
    xEye(c, 20, 12, 4, "1")
  else
    c:rect(19, 12, 7, 5, "6"); c:rect(20, 14, 3, 3, "R"); c:px(21, 15, "1")
    c:rect(19, 12, 7, pose.attack and 1 or 2, "v"); c:hline(19, 25, 17, "p")
  end
  -- joint
  if not pose.dead then
    c:rect(0, 24 + open, 6, 2, "6"); c:px(0, 24 + open, "o")
    local drift = pose.alt and 1 or 0
    c:px(1 + drift, 21 + open, "4"); c:px(0 + drift, 18 + open, "4"); c:px(2 - drift, 15, "3")
  end
end

-- ------------------------------------------------------------------ Étudiant post-examen (eau)
-- En larmes, sa copie ratée dans les mains. À l'attaque, les larmes giclent vers le joueur.

local function etudiant(c, pose)
  local sob = pose.alt and 1 or 0
  -- jambes et corps
  c:rect(23, 52, 7, 10, "u"); c:rect(34, 52, 7, 10, "u"); c:rect(21, 61, 9, 2, "1"); c:rect(34, 61, 9, 2, "1")
  c:ellipse(32, 36, 14, 6, "b"); c:rect(19, 36, 27, 18, "b"); c:rect(40, 36, 6, 18, "u")
  -- copie
  c:rect(22, 40, 20, 14, "6"); c:frame(22, 40, 20, 14, "4")
  c:hline(25, 38, 47, "3"); c:hline(25, 38, 50, "3")
  c:line(26, 42, 30, 45, "R"); c:line(30, 42, 26, 45, "R"); c:disc(36, 43, 2, "R"); c:px(36, 43, "6")
  c:disc(21, 46, 3, "s"); c:disc(43, 46, 3, "s")
  local h = c:shift(0, sob)
  -- tête
  h:disc(32, 20, 12, "s"); h:ellipse(32, 11, 12, 6, "N"); h:rect(20, 12, 3, 9, "N"); h:rect(42, 12, 3, 8, "N")
  h:rect(24, 15, 4, 2, "N")
  if pose.hurt then
    xEye(h, 24, 18, 4, "1"); xEye(h, 35, 18, 4, "1")
  else
    h:hline(24, 29, 20, "1"); h:hline(35, 40, 20, "1"); h:px(24, 19, "1"); h:px(40, 19, "1")
    h:line(23, 16, 29, 17, "N"); h:line(41, 16, 35, 17, "N")
  end
  h:ellipse(32, 27, pose.attack and 4 or 3, pose.attack and 3 or 2, "1"); h:hline(30, 34, 28, "d")
  -- larmes
  if pose.attack then
    local reach = pose.attack * 7
    for i = 0, 3 do
      h:line(25, 21 + i, 25 - reach, 14 + i * 5, "C", 2)
      h:rect(22 - reach - i * 2, 12 + i * 6, 2, 2, "6")
    end
    h:line(37, 21, 37, 36, "C", 2)
  elseif not pose.dead then
    h:rect(26, 21, 2, 12 + sob * 2, "C"); h:rect(37, 21, 2, 14 - sob * 2, "C")
    h:px(26, 21, "6"); h:px(37, 21, "6")
    c:ellipse(14, 61, 6, 1, "c"); c:ellipse(49, 61, 5, 1, "c")
    c:px(18, 40 + sob * 4, "C"); c:px(47, 44 - sob * 3, "C")
  end
end

-- ------------------------------------------------------------------ Vafi (feu)
-- Le prof : lunettes, cravate, la feuille « 3b » à la main. À l'attaque, le stylo rouge (Correction).

local function vafi(c, pose)
  -- jambes et veste
  c:rect(20, 55, 8, 7, "2"); c:rect(33, 55, 8, 7, "2"); c:rect(18, 61, 10, 2, "1"); c:rect(33, 61, 10, 2, "1")
  c:ellipse(30, 32, 15, 6, "3"); c:rect(15, 32, 31, 24, "3"); c:rect(40, 32, 6, 24, "2")
  c:tri(24, 28, 37, 28, 30, 44, "6"); c:rect(29, 29, 3, 13, "R"); c:tri(28, 41, 32, 41, 30, 45, "r")
  c:line(24, 28, 29, 44, "2"); c:line(37, 28, 32, 44, "2")
  -- feuille « 3b »
  local sheet = pose.alt and 1 or 0
  c:rect(40, 36, 9, 5, "3"); c:disc(48, 38, 3, "s")
  c:rect(47, 22 - sheet, 15, 19, "6"); c:frame(47, 22 - sheet, 15, 19, "4")
  local y = 25 - sheet
  c:hline(49, 52, y, "1"); c:vline(52, y, y + 6, "1"); c:hline(50, 52, y + 3, "1"); c:hline(49, 52, y + 6, "1")
  c:vline(55, y, y + 6, "1"); c:hline(55, 58, y + 3, "1"); c:vline(58, y + 3, y + 6, "1"); c:hline(55, 58, y + 6, "1")
  c:hline(49, 59, y + 10, "3"); c:hline(49, 57, y + 13, "3")
  -- stylo rouge
  if pose.attack then
    local reach = pose.attack * 4
    c:rect(15 - reach, 36, reach + 4, 5, "3"); c:disc(13 - reach, 38, 3, "s")
    c:line(12 - reach, 37, 3 - reach, 31, "R", 2); c:px(2 - reach, 30, "6")
    if pose.attack == 2 then c:line(0, 22, 6, 28, "R"); c:line(6, 22, 0, 28, "R") end
  else
    c:rect(13, 34, 5, 14, "3"); c:disc(15, 49, 3, "s"); c:line(15, 50, 12, 57, "R", 2)
  end
  -- tête
  c:ellipse(30, 16, 10, 12, "s"); c:rect(26, 26, 8, 4, "s")
  c:arc(30, 16, 10, 12, 190, 350, "4"); c:rect(19, 12, 3, 8, "4"); c:rect(39, 12, 3, 8, "4")
  c:hline(24, 36, 5, "4")
  if pose.hurt then
    xEye(c, 23, 13, 4, "1"); xEye(c, 32, 13, 4, "1")
    c:line(20, 20, 27, 22, "1"); c:line(29, 21, 38, 18, "1")
    c:ellipse(29, 24, 3, 2, "1")
  else
    c:rect(22, 13, 7, 6, "6"); c:rect(31, 13, 7, 6, "6")
    c:frame(21, 12, 9, 8, "1"); c:frame(30, 12, 9, 8, "1"); c:hline(38, 41, 14, "1")
    c:rect(23, 15, 3, 3, "1"); c:rect(32, 15, 3, 3, "1")
    c:line(21, 9, 28, 11, "4", 2); c:line(38, 9, 31, 11, "4", 2)
    if pose.attack then c:rect(26, 23, 8, 3, "1"); c:hline(27, 32, 23, "6") else c:hline(26, 33, 24, "N") end
  end
  c:rect(28, 19, 2, 3, "n")
end

-- ------------------------------------------------------------------ Gourou du Sat (spirituel)
-- Moine en tailleur, robe magenta, barbe, troisième œil ; à l'attaque, regard en spirale.

local function gourou(c, pose)
  local bob = pose.alt and 1 or 0
  c:ellipse(32, 56, 22, 6, "m"); c:ellipse(32, 46, 15, 12, "m"); c:ellipse(32, 47, 12, 10, "M")
  c:line(20, 44, 12, 52, "m", 3); c:line(44, 44, 52, 52, "m", 3)
  c:disc(12, 53, 2, "s"); c:disc(52, 53, 2, "s")
  local h = c:shift(0, bob)
  h:disc(32, 24, 11, "s"); h:ellipse(32, 33, 7, 6, "5"); h:ellipse(32, 31, 5, 3, "s")
  h:disc(32, 17, 2, pose.attack and "M" or "m")
  for _, x in ipairs({ 27, 37 }) do
    if pose.hurt then
      xEye(h, x - 2, 22, 3, "1")
    elseif pose.attack then
      h:disc(x, 24, 2, "M"); h:px(x, 24, "6")
      h:arc(x, 24, 3, 3, 0, 300, "M")
    else
      h:hline(x - 2, x + 2, 24, "1")
    end
  end
  if pose.attack then
    for i = 1, pose.attack * 2 do h:arc(32, 24, 12 + i * 3, 12 + i * 3, 150, 210, "M") end
  end
end

-- ------------------------------------------------------------------ NG (algébrique)
-- Silhouette en pull cyan, lunettes, craie ; à l'attaque il écrit au tableau.

local function ng(c, pose)
  local bob = pose.alt and 1 or 0
  c:rect(18, 38, 28, 25, "c"); c:rect(20, 40, 24, 23, "C"); c:hline(18, 45, 38, "c")
  c:rect(30, 40, 4, 8, "6")
  local arm = pose.attack and (8 + pose.attack * 3) or 0
  c:line(20, 42, 12 - arm / 2, 50 - arm, "c", 3)
  c:disc(11 - arm / 2, 50 - arm, 2, "s"); c:rect(7 - arm / 2, 47 - arm, 3, 2, "6")
  if pose.attack then
    c:hline(2, 8, 20, "6"); c:px(4, 18, "6"); c:px(4, 22, "6"); c:hline(2, 9, 26, "C"); c:px(9, 24, "C")
  end
  local h = c:shift(0, bob)
  h:disc(32, 24, 11, "s"); h:rect(21, 12, 22, 6, "N"); h:rect(21, 17, 3, 6, "N")
  for _, x in ipairs({ 27, 37 }) do
    if pose.hurt then
      xEye(h, x - 2, 22, 3, "1")
    else
      h:frame(x - 3, 21, 7, 5, "1"); h:px(x, 23, "1")
    end
  end
  h:hline(30, 33, 23, "1")
  h:hline(28, 36, 30, pose.attack and "d" or "n")
end

-- ------------------------------------------------------------------ Hypercube misogyne (géométrique)
-- Tesseract en fil de fer jaune, yeux mauvais ; à l'attaque, un tunnel qui s'ouvre vers le joueur.

local function hypercube(c, pose)
  local bob = pose.alt and 1 or 0
  local b = c:shift(0, bob)
  b:frame(16, 20, 26, 26, "y"); b:frame(26, 12, 26, 26, "Y")
  b:line(16, 20, 26, 12, "Y"); b:line(41, 20, 51, 12, "Y"); b:line(16, 45, 26, 37, "y"); b:line(41, 45, 51, 37, "Y")
  b:frame(24, 26, 12, 12, "o"); b:line(16, 20, 24, 26, "o"); b:line(41, 45, 35, 37, "o")
  for _, x in ipairs({ 27, 33 }) do
    if pose.hurt then
      xEye(b, x - 2, 29, 3, "R")
    else
      b:rect(x - 1, 30, 3, 3, "6"); b:px(x, 31, "1")
      b:line(x - 2, 28, x + 2, 29, "R")
    end
  end
  b:line(28, 35, 32, 34, "R")
  if pose.attack then
    for i = 1, pose.attack + 1 do b:frame(14 - i * 4, 22 + i * 2, 8, 20 - i * 4, "Y") end
  end
  c:ellipse(32, 60, 16, 2, "2")
end

-- ------------------------------------------------------------------ Engeance des cookies (GLITCH)
-- Énorme cookie aux pépites, yeux rougis, fumée ; des pixels décalés (glitch).

local function engeance(c, pose)
  local bob = pose.alt and 1 or 0
  local b = c:shift(0, bob)
  b:disc(32, 36, 22, "n"); b:disc(32, 35, 20, "y"); b:arc(32, 36, 22, 22, 0, 360, "N")
  for _, p in ipairs({ { 20, 26 }, { 40, 24 }, { 27, 46 }, { 44, 44 }, { 17, 40 }, { 34, 52 } }) do
    b:disc(p[1], p[2], 2, "N")
  end
  for _, x in ipairs({ 25, 39 }) do
    if pose.hurt then
      xEye(b, x - 3, 30, 5, "1")
    else
      b:disc(x, 33, 4, "6"); b:disc(x, 34, 2, "R"); b:px(x, 34, "1")
      b:hline(x - 4, x + 4, 29, "y"); b:hline(x - 4, x + 4, 30, "n")
    end
  end
  if pose.attack then
    b:ellipse(31, 44, 7, 3 + pose.attack, "d"); b:hline(26, 36, 43, "6")
  else
    b:arc(31, 41, 6, 3, 20, 160, "d")
  end
  -- glitch : bandes décalées et pixels parasites
  b:rect(8, 30, 6, 2, "M"); b:rect(50, 38, 6, 2, "C"); b:px(5, 20, "G"); b:px(58, 50, "M")
  c:arc(52, 10, 3, 3, 90, 360, "4"); c:arc(56, 5, 2, 2, 180, 420, "3")
end

-- ------------------------------------------------------------------ Gobelin (plante)
-- Petit gobelin vert en sweat ; attaque « 67 » : les deux mains montent et descendent en alternance.

local function gobelin(c, pose)
  local bob = pose.alt and 1 or 0
  c:rect(20, 40, 24, 22, "v"); c:rect(22, 42, 20, 20, "g"); c:rect(29, 50, 6, 4, "v")
  -- mains : hauteur gauche / droite (pose.hands = { gauche, droite } en pixels vers le haut)
  local hands = pose.hands or { 0, 0 }
  local lh, rh = 46 - hands[1], 46 - hands[2]
  c:line(21, 44, 12, lh, "g", 3); c:disc(11, lh, 3, "G")
  c:line(42, 44, 51, rh, "g", 3); c:disc(52, rh, 3, "G")
  local h = c:shift(0, bob)
  h:disc(32, 27, 12, "G"); h:tri(20, 22, 6, 14, 22, 30, "G"); h:tri(44, 22, 58, 14, 42, 30, "G")
  h:tri(20, 22, 9, 16, 21, 27, "g"); h:tri(44, 22, 55, 16, 43, 27, "g")
  for _, x in ipairs({ 27, 37 }) do
    if pose.hurt then
      xEye(h, x - 2, 24, 3, "1")
    else
      h:rect(x - 2, 23, 5, 4, "Y"); h:px(x - (pose.hands and 1 or 0), 25, "1")
    end
  end
  if pose.hands then
    h:ellipse(32, 33, 4, 2, "1"); h:hline(30, 34, 32, "6")
  else
    h:hline(28, 36, 33, "v")
  end
  c:rect(23, 14, 18, 3, "R")
end

local function gobelinSprite()
  local sprite = standard("gobelin", gobelin)
  local function frame(left, right, dx)
    return function(lib)
      return lib.drawn(64, function(c) gobelin(c:shift(dx, 0), { hands = { left, right } }) end)
    end
  end
  -- « 67 » : main gauche en haut, puis la droite, deux fois (le moteur joue 4 frames d'attaque)
  sprite.animations[2] = { name = "attack", frames = {
    frame(10, 0, -2), frame(0, 10, -3), frame(10, 0, -3), frame(0, 10, -2),
  } }
  return sprite
end

-- ------------------------------------------------------------------ Nortin (boss du niveau 1, neutre)
-- Costaud, longs cheveux blonds avec frange, débardeur bleu clair, sourcils froncés. Mains vides, bras
-- le long du corps ; à l'attaque, le bras côté joueur part en gifle (Bitch slap).

local function nortin(c, pose)
  local bob = pose.alt and 1 or 0
  -- bras (derrière le corps) : à droite, toujours le long du corps
  c:ellipse(51, 47, 6, 11, "s"); c:vline(46, 40, 56, "p"); c:disc(52, 58, 4, "s")
  if pose.attack then
    -- gifle : bras tendu vers le joueur, main ouverte, traits de vitesse
    local reach = pose.attack * 3
    c:line(17, 40, 5 - reach, 33, "s", 6)
    c:disc(4 - reach, 34, 4, "s"); c:hline(0 - reach, 3 - reach, 31, "s"); c:hline(0 - reach, 3 - reach, 37, "s")
    c:hline(9 - reach, 15 - reach, 41, "p")
    if pose.attack == 2 then
      c:vline(10, 26, 30, "6"); c:vline(13, 24, 29, "6"); c:vline(16, 26, 30, "6")
    end
  else
    c:ellipse(13, 47, 6, 11, "s"); c:vline(18, 40, 56, "p"); c:disc(12, 58, 4, "s")
  end
  -- épaules, débardeur et bretelles
  c:ellipse(32, 41, 19, 7, "s")
  c:ellipse(32, 46, 16, 4, "B"); c:rect(16, 46, 33, 18, "B"); c:rect(43, 46, 6, 18, "b")
  c:ellipse(32, 42, 7, 4, "s")
  c:rect(19, 35, 4, 11, "B"); c:rect(42, 35, 4, 11, "B"); c:vline(45, 35, 45, "b")
  c:arc(32, 58, 12, 4, 200, 340, "b")
  local h = c:shift(0, bob)
  -- cheveux (derrière le visage), jusqu'aux épaules
  h:ellipse(32, 14, 16, 12, "Y"); h:rect(16, 14, 33, 21, "Y")
  h:vline(19, 18, 34, "y"); h:vline(22, 24, 34, "y"); h:vline(42, 24, 34, "y"); h:vline(45, 18, 34, "y")
  -- visage rond et cou
  h:rect(27, 31, 11, 5, "s")
  h:ellipse(32, 22, 11, 11, "s"); h:ellipse(32, 28, 12, 6, "s"); h:arc(32, 28, 12, 6, 20, 160, "p")
  -- frange
  h:ellipse(32, 11, 12, 5, "Y"); h:rect(20, 11, 25, 4, "Y")
  h:px(24, 15, "y"); h:px(28, 15, "y"); h:px(33, 15, "y"); h:px(38, 15, "y"); h:px(42, 14, "y")
  -- yeux et sourcils
  if pose.hurt then
    xEye(h, 24, 19, 3, "1"); xEye(h, 36, 19, 3, "1")
  else
    h:line(22, 16, 29, 19, "N", 2); h:line(42, 16, 35, 19, "N", 2)
    h:rect(25, 20, 3, 2, "1"); h:rect(37, 20, 3, 2, "1")
    h:hline(25, 27, 23, "p"); h:hline(37, 39, 23, "p")
  end
  -- nez et bouche
  h:rect(31, 21, 3, 5, "p"); h:hline(30, 34, 26, "n")
  if pose.hurt or pose.attack == 2 then
    h:ellipse(32, 30, 3, 2, "1")
  else
    h:arc(32, 32, 5, 2, 200, 340, "N")
  end
end

-- ------------------------------------------------------------------ Zo le StreMon (boss du niveau 2, algébrique)
-- Cheveux ondulés blond foncé, yeux bleus, lunettes rondes écaille, henley blanc sous une veste en cuir brun,
-- petite chaîne dorée ; il parle tout le temps (bouche ouverte). À l'attaque (Coup de teub), il fonce
-- vers le joueur, traits de vitesse.

local function zo(c, pose)
  local bob = pose.alt and 1 or 0
  -- bras (derrière le corps) : à droite, toujours le long du corps
  c:ellipse(50, 49, 6, 12, "n"); c:disc(51, 61, 3, "s")
  if pose.attack then
    c:ellipse(15, 47, 6, 10, "n"); c:disc(17, 56, 3, "s")
  else
    c:ellipse(14, 49, 6, 12, "n"); c:disc(13, 61, 3, "s")
  end
  -- veste en cuir brun ouverte (coutures), henley blanc
  c:ellipse(32, 43, 18, 6, "n"); c:rect(14, 43, 37, 21, "n")
  c:line(24, 41, 18, 50, "N", 2); c:line(40, 41, 46, 50, "N", 2)
  c:vline(19, 51, 63, "N"); c:vline(45, 51, 63, "N")
  c:vline(16, 46, 58, "o"); c:vline(48, 46, 56, "o")
  c:rect(25, 40, 15, 24, "6"); c:vline(39, 40, 63, "5"); c:vline(25, 40, 63, "5")
  c:vline(24, 40, 63, "N"); c:vline(40, 40, 63, "N")
  -- boutons du henley et chaîne
  c:px(32, 46, "4"); c:px(32, 49, "4"); c:px(32, 52, "4")
  c:arc(32, 40, 6, 4, 20, 160, "y")
  if pose.attack then
    for i = 0, 2 do c:hline(8 - pose.attack * 2, 13 - pose.attack * 2, 50 + i * 4, "6") end
  end
  local h = c:shift(0, bob)
  -- cou et visage
  h:rect(28, 30, 9, 8, "s"); h:hline(29, 35, 37, "5")
  h:ellipse(32, 22, 11, 12, "s")
  h:disc(21, 23, 2, "s")
  -- cheveux ondulés, du volume sur le dessus
  h:ellipse(32, 9, 16, 8, "y"); h:ellipse(24, 4, 5, 3, "y"); h:ellipse(36, 3, 6, 3, "y")
  h:rect(19, 9, 4, 10, "y"); h:rect(42, 9, 4, 9, "y")
  h:ellipse(26, 12, 6, 3, "y"); h:ellipse(38, 11, 7, 3, "y")
  for _, w in ipairs({ { 20, 7 }, { 25, 5 }, { 31, 4 }, { 37, 5 }, { 43, 7 } }) do
    h:arc(w[1], w[2] + 2, 3, 2, 180, 360, "Y")
  end
  h:arc(26, 14, 4, 2, 0, 180, "Y"); h:arc(37, 14, 4, 2, 0, 180, "Y")
  h:arc(23, 16, 2, 2, 270, 450, "y"); h:arc(29, 16, 2, 2, 270, 450, "y"); h:arc(41, 15, 2, 2, 90, 270, "y")
  h:vline(20, 12, 18, "n"); h:vline(44, 11, 16, "n"); h:px(31, 14, "n")
  -- lunettes rondes écaille
  h:arc(26, 21, 4, 4, 0, 360, "N"); h:arc(38, 21, 4, 4, 0, 360, "N"); h:hline(30, 34, 20, "N")
  h:line(22, 20, 20, 19, "N")
  if pose.hurt then
    xEye(h, 24, 19, 3, "1"); xEye(h, 36, 19, 3, "1")
  else
    h:rect(25, 20, 2, 2, "u"); h:rect(37, 20, 2, 2, "u")
    h:line(23, 15, 28, 16, "y"); h:line(36, 16, 41, 15, "y")
  end
  -- nez et bouche ouverte (il parle)
  h:vline(32, 22, 26, "p"); h:px(31, 26, "p")
  if pose.hurt or pose.attack == 2 then
    h:ellipse(31, 30, 3, 2, "1")
  else
    h:ellipse(31, 30, 3, 1, "d")
  end
  h:hline(29, 33, 29, "6")
end

-- ------------------------------------------------------------------ Choomah (trois variantes, plante)
-- Créatures jaune vif sans cou (la tête et le corps ne font qu'un), contour noir, petits yeux,
-- grosses lèvres brunes, plis du corps en traits sombres. Elles crient tout le temps.
-- choomahBlob : formes { ellipse cx, cy, rx, ry } ou { limb = { x1, y1, x2, y2, r } }, cernées de noir.

local function choomahBlob(c, shapes, fill)
  local function each(grow, l)
    for _, s in ipairs(shapes) do
      if s.limb then
        local x1, y1, x2, y2, r = table.unpack(s.limb)
        local steps = math.max(math.abs(x2 - x1), math.abs(y2 - y1), 1)
        for i = 0, steps do c:disc(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps, r + grow, l) end
      else
        c:ellipse(s[1], s[2], s[3] + grow, s[4] + grow, l)
      end
    end
  end
  each(1, "1"); each(0, fill or "Y")
end

local function smallX(c, x, y)
  c:line(x, y, x + 2, y + 2, "1"); c:line(x + 2, y, x, y + 2, "1")
end

local function choomahHair(c, strands)
  for _, h in ipairs(strands) do c:line(h[1], h[2], h[3], h[4], "2") end
end

-- Lèvres : fermées (trait) ou ouvertes (il crie).
local function choomahLips(c, x, y, rx, ry, open)
  c:ellipse(x, y, rx + 1, ry + 1, "1"); c:ellipse(x, y, rx, ry, "N")
  if open then
    c:ellipse(x, y, math.max(rx - 2, 1), math.max(ry - 1, 1), "1")
  else
    c:hline(x - rx + 1, x + rx - 1, y, "1"); c:hline(x - rx + 2, x + rx - 2, y - ry, "n")
  end
end

-- Choomah : un haricot debout, petits bras, nez tombant, lèvres de côté.
local function choomah(c, pose)
  local bob = pose.alt and 1 or 0
  local u = c:shift(0, bob)
  local reach = pose.attack and { 20, 36, 7 - pose.attack * 2, 36, 2 } or { 21, 36, 17, 48, 2 }
  choomahBlob(c, { { limb = { 28, 52, 27, 60, 3 } }, { limb = { 37, 52, 38, 60, 3 } } })
  c:hline(23, 30, 62, "1"); c:hline(35, 42, 62, "1")
  choomahBlob(u, { { 33, 30, 12, 18 }, { 33, 43, 13, 11 }, { limb = reach }, { limb = { 45, 36, 48, 48, 2 } } })
  if pose.attack then for i = -1, 1 do u:line(reach[3], 36, reach[3] - 3, 36 + i * 2, "Y") end end
  choomahHair(u, { { 33, 11, 31, 8 }, { 36, 11, 38, 7 }, { 35, 11, 35, 8 } })
  -- plis : pectoraux, ventre, genoux
  u:arc(28, 37, 4, 2, 0, 160, "y"); u:arc(38, 37, 4, 2, 20, 180, "y")
  u:hline(31, 34, 44, "y"); u:hline(29, 31, 48, "y"); u:vline(42, 42, 46, "y")
  c:hline(26, 28, 56, "y"); c:hline(37, 39, 56, "y")
  -- visage tourné vers la gauche
  if pose.hurt then
    smallX(u, 23, 21); smallX(u, 31, 20)
  else
    u:rect(24, 22, 2, 2, "1"); u:rect(32, 21, 2, 2, "1")
    u:line(22, 20, 26, 21, "y"); u:line(31, 19, 35, 19, "y")
  end
  u:line(28, 23, 26, 28, "y"); u:px(27, 28, "1"); u:px(25, 28, "y")
  choomahLips(u, 26, 32, 5, 2, pose.attack or pose.hurt)
end

-- Choomah pressé : maigre, bras en l'air, jambes écartées, il hurle bouche grande ouverte.
local function choomahPresse(c, pose)
  local flap = pose.alt and 3 or 0
  local left, right
  if pose.attack then
    left = { { limb = { 25, 32, 12 - pose.attack * 2, 30, 2 } } }
    right = { { limb = { 40, 32, 50, 22, 2 } }, { limb = { 50, 22, 53, 14, 2 } } }
  else
    left = { { limb = { 25, 32, 15, 26 + flap, 2 } }, { limb = { 15, 26 + flap, 11, 17 + flap, 2 } } }
    right = { { limb = { 40, 32, 50, 27 - flap, 2 } }, { limb = { 50, 27 - flap, 54, 18 - flap, 2 } } }
  end
  local shapes = { { 32, 25, 9, 13 }, { 32, 39, 9, 10 },
    { limb = { 28, 47, 21 - flap // 3, 58, 3 } }, { limb = { 37, 47, 44, 58, 3 } },
    { 18 - flap // 3, 60, 4, 2 }, { 46, 60, 4, 2 } }
  for _, l in ipairs(left) do shapes[#shapes + 1] = l end
  for _, l in ipairs(right) do shapes[#shapes + 1] = l end
  choomahBlob(c, shapes)
  -- doigts écartés
  local hl = left[#left].limb; local hr = right[#right].limb
  for i = -1, 1 do
    c:line(hl[3], hl[4], hl[3] - 4, hl[4] - 1 + i * 3, "Y")
    c:line(hr[3], hr[4], hr[3] + 1 + i * 2, hr[4] - 5, "Y")
  end
  choomahHair(c, { { 31, 12, 29, 7 }, { 33, 12, 34, 6 }, { 35, 13, 38, 9 } })
  c:px(29, 6, "c"); c:px(34, 5, "c"); c:px(38, 8, "c")
  -- plis
  c:arc(32, 40, 5, 2, 20, 160, "y"); c:hline(30, 33, 45, "y"); c:line(24, 51, 22, 54, "y"); c:line(41, 51, 42, 54, "y")
  -- visage : sourcils froncés, bouche en O
  if pose.hurt then
    smallX(c, 23, 18); smallX(c, 32, 17)
  else
    c:rect(24, 19, 2, 2, "1"); c:rect(33, 18, 2, 2, "1"); c:px(35, 18, "6")
    c:line(22, 15, 27, 18, "1"); c:line(36, 15, 32, 17, "1")
  end
  choomahLips(c, 28, 27, 4, pose.hurt and 3 or 4, true)
end

-- Choomah costaud : massif, sourcils lourds, gros nez, énormes lèvres, longs bras à griffes.
local function choomahCostaud(c, pose)
  local bob = pose.alt and 1 or 0
  local u = c:shift(0, bob)
  local reach = pose.attack and { 19, 32, 8 - pose.attack * 2, 40, 3 } or { 19, 32, 11, 50, 3 }
  choomahBlob(c, { { limb = { 26, 52, 25, 60, 5 } }, { limb = { 39, 52, 40, 60, 5 } } })
  c:hline(19, 31, 63, "1"); c:hline(34, 46, 63, "1")
  choomahBlob(u, { { 32, 26, 15, 20 }, { 32, 42, 16, 13 }, { limb = reach }, { limb = { 46, 32, 53, 50, 3 } } })
  -- griffes
  for i = -1, 1 do
    u:line(reach[3], reach[4] + 2, reach[3] - 2 + i * 2, reach[4] + 6, "Y")
    u:line(53, 52, 53 + i * 2, 57, "Y")
  end
  choomahHair(u, { { 21, 13, 17, 10 }, { 43, 12, 47, 9 }, { 44, 14, 48, 13 }, { 33, 6, 35, 3 } })
  -- plis : pectoraux, abdos, bras
  u:arc(25, 38, 6, 3, 0, 170, "y"); u:arc(39, 38, 6, 3, 10, 180, "y"); u:vline(32, 36, 42, "y")
  for _, y in ipairs({ 45, 49 }) do u:hline(27, 29, y, "y"); u:hline(35, 37, y, "y") end
  u:line(48, 38, 49, 42, "y"); u:line(15, 38, 14, 42, "y")
  c:hline(23, 26, 57, "y"); c:hline(39, 42, 57, "y")
  -- visage : arcade lourde, yeux plissés, plis des joues
  u:hline(19, 40, 18, "y"); u:hline(20, 39, 19, "y")
  if pose.hurt then
    xEye(u, 22, 20, 3, "1"); xEye(u, 33, 20, 3, "1")
  else
    u:hline(22, 27, 21, "1"); u:hline(33, 38, 21, "1"); u:px(24, 22, "1"); u:px(35, 22, "1")
    u:hline(23, 26, 23, "y"); u:hline(34, 37, 23, "y")
  end
  u:line(30, 22, 27, 28, "y"); u:line(27, 28, 30, 29, "1")
  u:arc(19, 30, 3, 4, 120, 240, "y"); u:arc(40, 29, 3, 4, -60, 60, "y")
  choomahLips(u, 26, 32, 7, 3, pose.attack == 2 or pose.hurt)
end

-- ------------------------------------------------------------------ Colonel Eclatax (boss du niveau 3, plante)
-- Officier d'opérette : haute casquette verte à visière noire et insigne doré, grosse moustache noire
-- recourbée, favoris, joues roses, sourire satisfait ; veste verte à épaulettes rouges frangées d'or,
-- col blanc et cravate noire, médailles. À l'attaque (Pomme de terrax), il lance une pomme de terre.

local function colonel(c, pose)
  local bob = pose.alt and 1 or 0
  -- bras (derrière le corps) : à droite, le long du corps
  c:ellipse(51, 50, 6, 12, "v"); c:disc(52, 61, 3, "s")
  if pose.attack then
    -- bras tendu vers le joueur, pomme de terre en vol
    c:ellipse(12, 46, 9, 5, "v"); c:disc(4, 46, 3, "s")
    local px = pose.attack == 2 and -2 or 3
    c:ellipse(px, 38, 4, 3, "n"); c:px(px - 1, 37, "N"); c:px(px + 2, 39, "N"); c:px(px, 39, "N")
  else
    c:ellipse(13, 50, 6, 12, "v"); c:disc(12, 61, 3, "s")
  end
  -- veste verte, épaulettes rouges à franges dorées
  c:ellipse(32, 44, 19, 6, "g"); c:rect(13, 44, 39, 20, "g")
  c:vline(32, 50, 63, "v"); c:vline(19, 50, 63, "v"); c:vline(45, 50, 63, "v")
  c:ellipse(16, 41, 7, 3, "r"); c:ellipse(48, 41, 7, 3, "r")
  for x = 10, 22, 2 do c:vline(x, 43, 45, "Y") end
  for x = 42, 54, 2 do c:vline(x, 43, 45, "Y") end
  -- col blanc, cravate noire, revers rouges
  c:tri(25, 38, 39, 38, 32, 49, "6")
  c:tri(23, 38, 28, 38, 27, 45, "r"); c:tri(36, 38, 41, 38, 37, 45, "r")
  c:rect(31, 40, 3, 3, "1"); c:tri(30, 43, 34, 43, 32, 50, "1")
  -- boutons dorés et médailles
  c:px(27, 53, "Y"); c:px(27, 58, "Y"); c:px(37, 53, "Y"); c:px(37, 58, "Y")
  c:rect(39, 47, 2, 2, "R"); c:rect(42, 47, 2, 2, "b"); c:rect(45, 47, 2, 2, "Y")
  c:px(40, 49, "Y"); c:px(43, 49, "Y"); c:px(46, 49, "y")
  local h = c:shift(0, bob)
  -- cou, visage, gros menton carré en avant, oreille (côté droit : il regarde vers la gauche)
  h:rect(29, 32, 9, 6, "s"); h:hline(30, 36, 37, "o")
  h:ellipse(31, 24, 11, 10, "s")
  h:ellipse(29, 32, 9, 5, "s"); h:rect(21, 29, 16, 4, "s")
  h:hline(24, 33, 37, "o"); h:px(28, 35, "o")
  h:disc(42, 24, 3, "s"); h:px(42, 24, "o")
  -- favoris noirs
  h:rect(38, 18, 3, 6, "1")
  -- joues roses
  h:ellipse(36, 27, 3, 2, "p"); h:ellipse(22, 26, 2, 1, "p")
  -- yeux et sourcils
  if pose.hurt then
    xEye(h, 23, 19, 3, "1"); xEye(h, 32, 19, 3, "1")
  else
    h:rect(24, 21, 2, 2, "1"); h:rect(33, 21, 2, 2, "1")
    h:line(23, 19, 27, 20, "1"); h:line(32, 20, 36, 19, "1")
  end
  -- nez rond
  h:ellipse(28, 25, 2, 2, "s"); h:px(26, 26, "o")
  -- bouche, sur le menton : sourire satisfait, ouverte quand il crie ou qu'il a mal
  if pose.hurt or pose.attack == 2 then
    h:ellipse(29, 33, 3, 2, "1"); h:hline(27, 31, 32, "6")
  else
    h:hline(25, 32, 32, "6"); h:hline(26, 31, 33, "6"); h:hline(26, 31, 34, "o")
  end
  -- grosse moustache en guidon : épaisse, reflets gris, pointes enroulées vers le haut
  local tip = pose.alt and -1 or 0
  h:ellipse(24, 29, 5, 2, "1"); h:ellipse(33, 29, 5, 2, "1"); h:rect(27, 27, 5, 3, "1")
  h:hline(21, 25, 28, "2"); h:hline(32, 36, 28, "2"); h:px(29, 27, "2")
  h:line(19, 30, 16, 28 + tip, "1", 2); h:arc(15, 26 + tip, 2, 2, 0, 270, "1")
  h:line(37, 30, 40, 28 + tip, "1", 2); h:arc(42, 26 + tip, 2, 2, 270, 540, "1")
  -- haute casquette verte, plateau qui monte vers l'avant (la gauche)
  h:ellipse(32, 11, 14, 5, "g"); h:ellipse(25, 6, 10, 5, "g"); h:tri(15, 9, 22, 1, 40, 9, "g")
  h:arc(25, 6, 10, 5, 190, 300, "G"); h:hline(20, 42, 11, "v")
  -- bandeau rouge, cordon doré, visière noire brillante
  h:rect(18, 12, 28, 3, "r"); h:hline(18, 45, 15, "Y")
  h:ellipse(22, 17, 9, 2, "1"); h:hline(16, 22, 16, "3")
  -- insigne doré (feuilles)
  h:tri(25, 10, 28, 3, 31, 10, "Y"); h:tri(22, 10, 24, 5, 27, 10, "y"); h:tri(29, 10, 32, 5, 34, 10, "y")
  h:px(28, 8, "6")
  -- cheveux noirs sous la casquette, derrière l'oreille
  h:rect(41, 16, 4, 5, "1")
end

-- ------------------------------------------------------------------ placeholders
-- Clarence et le boss sont dessinés à la main : une silhouette « ? » en attendant, avec le
-- seul tag idle. Remplacer le dessin dans le .aseprite (le script n'y touchera plus).

local function placeholder(id, crown)
  return { id = id, animations = { { name = "idle", frames = { function(lib)
    return lib.drawn(64, function(c)
      c:disc(32, 17, 10, "2"); c:rect(16, 29, 32, 30, "2")
      for a = 0, 350, 12 do c:px(32 + 10 * math.cos(math.rad(a)), 17 + 10 * math.sin(math.rad(a)), "3") end
      for i = 16, 47, 3 do c:px(i, 29, "3"); c:px(i, 58, "3") end
      for i = 29, 58, 3 do c:px(16, i, "3"); c:px(47, i, "3") end
      c:arc(32, 39, 5, 5, 180, 380, "3"); c:arc(32, 39, 4, 5, 180, 380, "3")
      c:vline(33, 44, 48, "3"); c:vline(32, 44, 48, "3"); c:rect(32, 51, 2, 2, "3")
      if crown then
        c:rect(24, 4, 17, 4, "y"); c:tri(24, 4, 27, 4, 24, 0, "Y"); c:tri(30, 4, 34, 4, 32, 0, "Y")
        c:tri(37, 4, 40, 4, 40, 0, "Y"); c:hline(24, 40, 7, "Y")
      end
    end)
  end } } } }
end

return {
  caddieEnerve, seauDeFuite,
  standard("came", came), standard("paresseux", paresseux), standard("le-peneu", peneu),
  standard("t-rex-defonce", trex), standard("etudiant-post-examen", etudiant), standard("vafi", vafi),
  standard("gourou-du-sat", gourou), standard("ng", ng), standard("hypercube-misogyne", hypercube),
  gobelinSprite(), standard("engeance-des-cookies", engeance), standard("nortin", nortin), standard("zo", zo),
  standard("choomah", choomah), standard("choomah-presse", choomahPresse),
  standard("choomah-costaud", choomahCostaud),
  standard("colonel", colonel),
  placeholder("clarence", false), placeholder("boss", true),
}

