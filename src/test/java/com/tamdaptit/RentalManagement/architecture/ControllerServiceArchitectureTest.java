package com.tamdaptit.rentalmanagement.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

class ControllerServiceArchitectureTest {
    private static final String BASE = "com.tamdaptit.rentalmanagement";

    @Test
    void controllersDependOnServiceInterfacesImplementedInImplPackage() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var controllers = scanner.findCandidateComponents(BASE);
        assertFalse(controllers.isEmpty());
        for (var definition : controllers) {
            var controller = Class.forName(definition.getBeanClassName());
            for (var field : controller.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                var dependency = field.getType();
                if (!dependency.getPackageName().startsWith(BASE)) continue;
                assertFalse(dependency.getPackageName().contains(".repository"),
                        controller.getSimpleName()+" must access persistence through a service");
                if (!dependency.getPackageName().contains(".service")) continue;
                assertTrue(dependency.isInterface(), controller.getSimpleName()+"."+field.getName()
                        +" must depend on a service interface");
                var implementation = Class.forName(dependency.getPackageName()+".impl."
                        +dependency.getSimpleName()+"Impl");
                assertTrue(dependency.isAssignableFrom(implementation));
                assertTrue(implementation.isAnnotationPresent(Service.class), implementation.getName());
            }
        }
    }
}
