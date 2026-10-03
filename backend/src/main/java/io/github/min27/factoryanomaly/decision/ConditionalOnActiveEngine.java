package io.github.min27.factoryanomaly.decision;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@code engine.active}에 이름이 있을 때만 엔진 Bean을 등록한다 (D-019).
 * 설정이 없으면 {@link EngineProperties}의 기본값과 같이 rule만 등록된다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnActiveEngine.OnActiveEngineCondition.class)
public @interface ConditionalOnActiveEngine {

    /** 엔진 이름 ({@link DecisionEngine#name()}과 같아야 한다) */
    String value();

    class OnActiveEngineCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String engine = (String) metadata.getAnnotationAttributes(ConditionalOnActiveEngine.class.getName()).get("value");
            List<String> active = Binder.get(context.getEnvironment())
                    .bind("engine.active", Bindable.listOf(String.class))
                    .orElse(List.of("rule"));
            return active.contains(engine);
        }
    }
}
