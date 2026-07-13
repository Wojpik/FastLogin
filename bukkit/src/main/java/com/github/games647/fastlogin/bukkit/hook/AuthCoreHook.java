/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class AuthCoreHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;
    private final Plugin authCorePlugin;

    private final Method isRegisteredMethod;
    private final Method forceLoginMethod;
    private final Method forceRegisterMethod;

    public AuthCoreHook(FastLoginBukkit plugin) throws ReflectiveOperationException {
        this.plugin = plugin;

        Plugin found = Bukkit.getPluginManager().getPlugin("AuthCore");
        if (found == null) {
            throw new IllegalStateException("Plugin AuthCore nie jest zaladowany");
        }
        this.authCorePlugin = found;

        Class<?> authCoreClass = authCorePlugin.getClass();
        this.isRegisteredMethod = authCoreClass.getMethod("fastLoginIsRegistered", String.class);
        this.forceLoginMethod = authCoreClass.getMethod("fastLoginForceLogin", Player.class);
        this.forceRegisterMethod = authCoreClass.getMethod("fastLoginForceRegister", Player.class, String.class);
    }

    @Override
    public boolean forceLogin(Player player) {
        return callSync(() -> (boolean) forceLoginMethod.invoke(authCorePlugin, player));
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return callSync(() -> (boolean) forceRegisterMethod.invoke(authCorePlugin, player, password));
    }

    @Override
    public boolean isRegistered(String playerName) throws Exception {
        return (boolean) isRegisteredMethod.invoke(authCorePlugin, playerName);
    }

    private boolean callSync(Callable<Boolean> task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                plugin.getLog().error("Blad AuthCoreHook (glowny watek)", e);
                return false;
            }
        }

        try {
            Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(plugin, task);
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            plugin.getLog().error("Blad AuthCoreHook (async->sync)", e);
            return false;
        }
    }
}
