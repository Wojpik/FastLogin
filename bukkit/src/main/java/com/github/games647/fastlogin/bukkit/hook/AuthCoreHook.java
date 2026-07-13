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

/**
 * Integracja z customowym pluginem AuthCore (nie oficjalny hook FastLoginu - dodany
 * recznie dla tego konkretnego serwera). Uzywa refleksji zamiast twardej zaleznosci
 * kompilacyjnej, zeby oba projekty (FastLogin i AuthCore) mogly pozostac osobnymi,
 * niezaleznie budowanymi projektami Maven.
 *
 * WYMAGA, zeby AuthCorePlugin (pl.auth.core.AuthCorePlugin) mial nastepujace
 * publiczne metody (patrz AuthCorePlugin.java w projekcie mc-auth-system):
 *   boolean fastLoginIsRegistered(String username)
 *   boolean fastLoginForceLogin(Player player)
 *   boolean fastLoginForceRegister(Player player, String generatedPassword)
 *
 * Zarejestrowany w DelayedAuthHook.getAuthHook() pod warunkiem, ze plugin "AuthCore"
 * jest wlaczony na tym serwerze (nazwa wyprowadzona z nazwy tej klasy: AuthCoreHook -> "AuthCore").
 */
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
        // forceLogin/forceRegister moga byc wywolywane ASYNC przez FastLogin
        // (patrz dokumentacja AuthPlugin#forceLogin) - AuthCore manipuluje
        // ekwipunkiem/widocznoscia encji, co MUSI dziac sie na glownym watku
        // serwera, wiec przelaczamy sie na niego i czekamy na wynik.
        return callSync(() -> (boolean) forceLoginMethod.invoke(authCorePlugin, player));
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return callSync(() -> (boolean) forceRegisterMethod.invoke(authCorePlugin, player, password));
    }

    @Override
    public boolean isRegistered(String playerName) throws Exception {
        // Zwykly odczyt z bazy - bezpieczny poza glownym watkiem, bez potrzeby
        // przelaczania (JDBC nie jest zwiazane z watkiem Bukkit).
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
