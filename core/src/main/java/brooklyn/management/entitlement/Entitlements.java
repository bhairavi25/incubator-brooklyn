/*
 * Copyright 2009-2014 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.management.entitlement;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

public class Entitlements {

    private static final Logger log = LoggerFactory.getLogger(Entitlements.class);
    
    // ------------------- individual permissions
    
    public static EntitlementClass<Entity> SEE_ENTITY = new BasicEntitlementClassDefinition<Entity>("entity.see", Entity.class);
    
    public static EntitlementClass<EntityAndItem<String>> SEE_SENSOR = new BasicEntitlementClassDefinition<EntityAndItem<String>>("sensor.see", EntityAndItem. typeToken(String.class));
    
    public static EntitlementClass<EntityAndItem<String>> INVOKE_EFFECTOR = new BasicEntitlementClassDefinition<EntityAndItem<String>>("effector.invoke", EntityAndItem. typeToken(String.class));
    
    /** the permission to deploy an application, where parameter is some representation of the app to be deployed (spec instance or yaml plan) */
    public static EntitlementClass<EntityAndItem<Object>> DEPLOY_APPLICATION = new BasicEntitlementClassDefinition<EntityAndItem<Object>>("app.deploy", EntityAndItem. typeToken(Object.class));

    /** catch-all for catalog, locations, scripting, usage, etc; 
     * NB1: all users can see HA status;
     * NB2: this may be refactored and deprecated in future */
    public static EntitlementClass<Void> SEE_ALL_SERVER_INFO = new BasicEntitlementClassDefinition<Void>("server.info.all.see", Void.class);
    
    /** permission to run untrusted code or embedded scripts at the server; 
     * secondary check required for any operation which could potentially grant root-level access */ 
    public static EntitlementClass<Void> ROOT = new BasicEntitlementClassDefinition<Void>("root", Void.class);

    
    public static class EntityAndItem<T> {
        final Entity entity;
        final T item;
        public static <TT> TypeToken<EntityAndItem<TT>> typeToken(Class<TT> type) {
            return new TypeToken<Entitlements.EntityAndItem<TT>>() {
                private static final long serialVersionUID = -738154831809025407L;
            };
        }
        public EntityAndItem(Entity entity, T item) {
            this.entity = entity;
            this.item = item;
        }
        public Entity getEntity() {
            return entity;
        }
        public T getItem() {
            return item;
        }
        public static <T> EntityAndItem<T> of(Entity entity, T item) {
            return new EntityAndItem<T>(entity, item);
        }
    }

    // ------------- permission sets -------------
    
    /** always ALLOW access to everything */
    public static EntitlementManager root() {
        return new EntitlementManager() {
            @Override
            public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                return true;
            }
        };
    }

    /** always DENY access to anything which requires entitlements */
    public static EntitlementManager minimal() {
        return new EntitlementManager() {
            @Override
            public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                return false;
            }
        };
    }

    public static class FineGrainedEntitlements {
    
        public static EntitlementManager anyOf(final EntitlementManager ...checkers) {
            return new EntitlementManager() {
                @Override
                public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                    for (EntitlementManager checker: checkers)
                        if (checker.isEntitled(context, permission, typeArgument))
                            return true;
                    return false;
                }
            };
        }
        
        public static EntitlementManager allOf(final EntitlementManager ...checkers) {
            return new EntitlementManager() {
                @Override
                public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                    for (EntitlementManager checker: checkers)
                        if (checker.isEntitled(context, permission, typeArgument))
                            return true;
                    return false;
                }
            };
        }

        public static <U> EntitlementManager allowing(EntitlementClass<U> permission, Predicate<U> test) {
            return new SinglePermissionEntitlementChecker<U>(permission, test);
        }

        public static <U> EntitlementManager allowing(EntitlementClass<U> permission) {
            return new SinglePermissionEntitlementChecker<U>(permission, Predicates.<U>alwaysTrue());
        }

        public static class SinglePermissionEntitlementChecker<U> implements EntitlementManager {
            final EntitlementClass<U> permission;
            final Predicate<U> test;
            
            protected SinglePermissionEntitlementChecker(EntitlementClass<U> permission, Predicate<U> test) {
                this.permission = permission;
                this.test = test;
            }
            
            @SuppressWarnings("unchecked")
            @Override
            public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                if (!Objects.equal(this.permission, permission)) return false;
                return test.apply((U)typeArgument);
            }
            
        }
        public static EntitlementManager seeNonSecretSensors() {
            return allowing(SEE_SENSOR, new Predicate<EntityAndItem<String>>() {
                @Override
                public boolean apply(EntityAndItem<String> input) {
                    if (input == null) return false;
                    return !Entities.isSecret(input.getItem());
                }
            });
        }
        
    }
    
    /** allow read-only */
    public static EntitlementManager readOnly() {
        return FineGrainedEntitlements.anyOf(
            FineGrainedEntitlements.allowing(SEE_ENTITY),
            FineGrainedEntitlements.seeNonSecretSensors()
        );
    }

    // ------------- lookup conveniences -------------

    private static class PerThreadEntitlementContextHolder {
        public static final ThreadLocal<EntitlementContext> perThreadEntitlementsContextHolder = new ThreadLocal<EntitlementContext>();
    }

    public static EntitlementContext getEntitlementContext() {
        return PerThreadEntitlementContextHolder.perThreadEntitlementsContextHolder.get();
    }

    public static void setEntitlementContext(EntitlementContext context) {
        EntitlementContext oldContext = PerThreadEntitlementContextHolder.perThreadEntitlementsContextHolder.get();
        if (oldContext!=null && context!=null) {
            log.warn("Changing entitlement context from "+oldContext+" to "+context+"; context should have been reset or extended, not replaced");
        }
        PerThreadEntitlementContextHolder.perThreadEntitlementsContextHolder.set(context);
    }
    
    public static void clearEntitlementContext() {
        PerThreadEntitlementContextHolder.perThreadEntitlementsContextHolder.set(null);
    }
    
    public static <T> boolean isEntitled(EntitlementManager checker, EntitlementClass<T> permission, T typeArgument) {
        return checker.isEntitled(getEntitlementContext(), permission, typeArgument);
    }

    public static <T> void requireEntitled(EntitlementManager checker, EntitlementClass<T> permission, T typeArgument) {
        if (!isEntitled(checker, permission, typeArgument)) {
            throw new NotEntitledException(getEntitlementContext(), permission, typeArgument);
        }
    }

    // ----------------- initialization ----------------

    public static ConfigKey<String> GLOBAL_ENTITLEMENT_MANAGER = ConfigKeys.newStringConfigKey("brooklyn.entitlements.global", 
        "Class for entitlements in effect globally; many instances accept further per user entitlements; "
        + "short names 'minimal', 'readonly', or 'root' are permitted here, with the default 'root' giving full access to all declared users",
        "root");
    
    public static EntitlementManager newManager(ResourceUtils loader, BrooklynProperties brooklynProperties) {
        EntitlementManager result = newGlobalManager(loader, brooklynProperties);
        // TODO per user settings
        return result;
    }
    private static EntitlementManager newGlobalManager(ResourceUtils loader, BrooklynProperties brooklynProperties) {
        String type = brooklynProperties.getConfig(GLOBAL_ENTITLEMENT_MANAGER);
        if ("root".equalsIgnoreCase(type)) return new PerUserEntitlementManagerWithDefault(root());
        if ("readonly".equalsIgnoreCase(type)) return new PerUserEntitlementManagerWithDefault(readOnly());
        if ("minimal".equalsIgnoreCase(type)) return new PerUserEntitlementManagerWithDefault(minimal());
        if (Strings.isNonBlank(type)) {
            try {
                return (EntitlementManager) loader.getLoader().loadClass(type).newInstance();
            } catch (Exception e) { throw Exceptions.propagate(e); }
        }
        throw new IllegalStateException("Invalid entitlement manager specified: '"+type+"'");
    }
    

}
