package com.shrtrl.api;

import java.lang.reflect.Constructor;
import java.util.Map;

import static org.mockito.Mockito.mock;

final class ControllerTestSupport {

    private ControllerTestSupport() {
    }

    static <T> T createController(
            Class<T> controllerType,
            Map<Class<?>, Object> knownDependencies) {
        try {
            Constructor<?> constructor = controllerType.getDeclaredConstructors()[0];
            Object[] arguments = new Object[constructor.getParameterCount()];
            Class<?>[] parameterTypes = constructor.getParameterTypes();

            for (int index = 0; index < parameterTypes.length; index++) {
                arguments[index] = knownDependencies.getOrDefault(
                        parameterTypes[index],
                        mock(parameterTypes[index]));
            }

            return controllerType.cast(constructor.newInstance(arguments));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not construct " + controllerType.getSimpleName(),
                    exception);
        }
    }
}
