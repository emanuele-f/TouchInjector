#!/usr/bin/env python3
#
#  This file is part of TouchInjector.
#
#  TouchInjector is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  TouchInjector is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with TouchInjector. If not, see <http://www.gnu.org/licenses/>.
#
#  Copyright 2021 - Emanuele Faranda
#

import socket
import evdev
import time
import math
from select import select
from enum import Enum

# #######################################################

# NOTE: run "adb forward tcp:7070 tcp:7070" before starting
HOST = '127.0.0.1'
PORT = 7070

CONNECT_RETRY_INTERVAL = 3
AXIS_NOTIFY_INTERVAL = 0.010
MIN_DELTA = 0.015
DEBUG = False

# #######################################################

class GamepadKey(Enum):
  K_UNKNOWN = 0
  K_Y = 1
  K_B = 2
  K_A = 3
  K_X = 4
  K_UP = 5
  K_RBIGHT = 6
  K_DOWN = 7
  K_LBEFT = 8
  K_START = 9
  K_SELECT = 10
  K_LB = 11
  K_LT = 12
  K_RB = 13
  K_RT = 14
  K_HOME = 15
  K_RSTICK = 16
  K_LSTICK = 17
  # ~ K_SHARE = 18
  # ~ K_RSR = 19
  # ~ K_RSL = 20
  # ~ K_LSR = 21
  # ~ K_LSL = 22

# #######################################################

class Gamepad:
  def __init__(self, dev):
    self.dev = dev
    self.deadzone = 0.0
    self.axis_max = 32767
    self.mapping = {}
    self.l_x_axis = 0
    self.l_y_axis = 1
    self.r_x_axis = 3
    self.r_y_axis = 4

    self.l_stick = [0, 0]
    self.r_stick = [0, 0]

  def normalizeAxis(self, val):
    return float(val) / self.axis_max

  def removeDeadzone(self, pt):
    hh = math.hypot(pt[1], pt[0])

    if(hh <= self.deadzone):
      return [0.0, 0.0]

    # Scale the vector in the (0, 1) domain as it started from the edge
    # of the deadzone
    inzone = ((hh - self.deadzone) / (1.0 - self.deadzone))
    scale = inzone / hh
    return [unitRange(pt[0] * scale), unitRange(pt[1] * scale)]

# #######################################################

class Xbox360Gamepad(Gamepad):
  LT_AXIS = 2
  RT_AXIS = 5
  TRIGGERS_EDGE_HIGH = 180
  TRIGGERS_EDGE_LOW = 60

  def __init__(self, dev):
    super().__init__(dev)

    self.deadzone = 0.43
    self.mapping = {
      308: GamepadKey.K_Y,
      305: GamepadKey.K_B,
      304: GamepadKey.K_A,
      307: GamepadKey.K_X,
      706: GamepadKey.K_UP,
      705: GamepadKey.K_RBIGHT,
      707: GamepadKey.K_DOWN,
      704: GamepadKey.K_LBEFT,
      315: GamepadKey.K_START,
      314: GamepadKey.K_SELECT,
      310: GamepadKey.K_LB,
      311: GamepadKey.K_RB,
      316: GamepadKey.K_HOME,
      318: GamepadKey.K_RSTICK,
      317: GamepadKey.K_LSTICK,
    }

# #######################################################

class JoyconsGamepad(Gamepad):
  def __init__(self, dev):
    super().__init__(dev)

    self.deadzone = 0.10
    self.mapping = {
      307: GamepadKey.K_Y,
      305: GamepadKey.K_B,
      304: GamepadKey.K_A,
      308: GamepadKey.K_X,
      545: GamepadKey.K_UP,
      547: GamepadKey.K_RBIGHT,
      544: GamepadKey.K_DOWN,
      546: GamepadKey.K_LBEFT,
      315: GamepadKey.K_START,
      314: GamepadKey.K_SELECT,
      # ~ 309: GamepadKey.K_SHARE,
      310: GamepadKey.K_LB,
      312: GamepadKey.K_LT,
      311: GamepadKey.K_RB,
      313: GamepadKey.K_RT,
      316: GamepadKey.K_HOME,
      318: GamepadKey.K_RSTICK,
      317: GamepadKey.K_LSTICK,
      # ~ 707: GamepadKey.K_RSR,
      # ~ 706: GamepadKey.K_RSL,
      # ~ 705: GamepadKey.K_LSR,
      # ~ 704: GamepadKey.K_LSL,
    }

# #######################################################

def findGamepad():
  global gamepad

  for devpath in evdev.list_devices():
    try:
      dev = evdev.InputDevice(devpath)

      if "Combined Joy-Cons" in dev.name:
        # https://github.com/DanielOgorchock/joycond
        gamepad = JoyconsGamepad(dev)
        break
      elif "Xbox 360" in dev.name:
        # xpad
        gamepad = Xbox360Gamepad(dev)
        break

      dev.close()
    except Exception as e:
      print(e)

  assert gamepad, "No supported gamepad not found"

  print("Using %s - %s" % (gamepad.dev.path, gamepad.dev.name))

# #######################################################

def unitRange(a):
  _min = -1.0
  _max = 1.0
  return min(max(a, _min), _max)

# #######################################################

def tryConnect():
  global server
  global connect_retry_t

  try:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, True)
    server.connect((HOST, PORT))

    print("Connected to server")
  except socket.error as e:
    print(str(e))
    server = None
    connect_retry_t = time.time() + CONNECT_RETRY_INTERVAL

# #######################################################

def trySend(msg):
  global server
  global connect_retry_t

  if DEBUG:
    print(msg)

  if not server:
    return

  try:
    server.send(msg.encode("ascii"))
  except socket.error as e:
    print(str(e))
    server.close()
    server = None
    connect_retry_t = time.time() + CONNECT_RETRY_INTERVAL

# #######################################################

gamepad = None
server = None
connect_retry_t = 0

# L, R
stick_changed = [False, False]
trigger_pressed = [False, False]

def main():
  global connect_retry_t

  last_sent_l_stick = [0, 0]
  last_sent_r_stick = [0, 0]
  next_axis_notify = 0

  findGamepad()
  tryConnect()

  while True:
    now = time.time()

    if (stick_changed[0] or stick_changed[1]) and (now >= next_axis_notify):
      next_axis_notify = now + AXIS_NOTIFY_INTERVAL
      msg = ""

      if stick_changed[0]:
        val = gamepad.removeDeadzone(gamepad.l_stick)

        if val != last_sent_l_stick:
          msg += "L_STICK|%.3f|%.3f\n" % (val[0], val[1])
          stick_changed[0] = False
          last_sent_l_stick = val

      if stick_changed[1]:
        val = gamepad.removeDeadzone(gamepad.r_stick)

        if val != last_sent_r_stick:
          msg += "R_STICK|%.3f|%.3f\n" % (val[0], val[1])
          stick_changed[1] = False
          last_sent_r_stick = val

      if msg:
        trySend(msg)

    if(not server and (now >= connect_retry_t)):
      tryConnect()

    rv = select([gamepad.dev], [], [], AXIS_NOTIFY_INTERVAL)

    if not rv[0]:
      continue

    # see also gamepad.dev.read_loop()
    for event in gamepad.dev.read():
      etype = event.type

      if etype == evdev.ecodes.EV_KEY:
        ekey = evdev.categorize(event)
        key = gamepad.mapping.get(ekey.scancode, GamepadKey.K_UNKNOWN)

        trySend("K_%s|%d\n" % (("DOWN" if ekey.keystate else "UP"), key.value))
      elif etype == evdev.ecodes.EV_ABS:
        ecode = event.code

        if isinstance(gamepad, Xbox360Gamepad) and \
            (((ecode == Xbox360Gamepad.LT_AXIS) or (ecode == Xbox360Gamepad.RT_AXIS))):
          # Convert LT/RT from axis to button
          val = event.value
          trigger = GamepadKey.K_LT if (ecode == Xbox360Gamepad.LT_AXIS) else GamepadKey.K_RT
          idx = 0 if (ecode == Xbox360Gamepad.LT_AXIS) else 1
          msg = ""

          if (not trigger_pressed[idx]) and (val >= Xbox360Gamepad.TRIGGERS_EDGE_HIGH):
            msg = "K_DOWN|%d\n" % (trigger.value)
            trigger_pressed[idx] = True
          elif trigger_pressed[idx] and (val <= Xbox360Gamepad.TRIGGERS_EDGE_LOW):
            msg = "K_UP|%d\n" % (trigger.value)
            trigger_pressed[idx] = False

          if msg:
            trySend(msg)
        else:
          is_left = ((ecode == gamepad.l_x_axis) or (ecode == gamepad.l_y_axis))
          idx = ((ecode == gamepad.l_y_axis) or (ecode == gamepad.r_y_axis))
          stick = gamepad.l_stick if is_left else gamepad.r_stick
          new_val = gamepad.normalizeAxis(event.value if (idx == 0) else -event.value)

          if abs(new_val - stick[idx]) >= MIN_DELTA:
            stick[idx] = new_val

            # Rate limiting
            stick_changed[0 if is_left else 1] = True
      elif etype != evdev.ecodes.EV_SYN:
        print("Unhanlded event: " + str(event.type))

# #######################################################

if __name__ == "__main__":
  try:
    main()
  finally:
    if gamepad and gamepad.dev:
      gamepad.dev.close()
    if server:
      server.close()
