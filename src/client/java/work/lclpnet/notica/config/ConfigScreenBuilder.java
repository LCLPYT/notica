package work.lclpnet.notica.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.config.ConfigManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.function.Consumer;

import static net.minecraft.network.chat.Component.translatable;
import static net.minecraft.network.chat.Component.translatableWithFallback;
import static work.lclpnet.notica.config.ConfigTranslations.*;

public class ConfigScreenBuilder implements ConfigScreenFactory<Screen> {

    private final ConfigManager<NoticaClientConfig> configManager;
    private final NoticaClientConfig config, defaultConfig;

    public ConfigScreenBuilder(ConfigManager<NoticaClientConfig> configManager) {
        this.configManager = configManager;
        this.config = configManager.config();
        this.defaultConfig = new NoticaClientConfig();
    }

    private void save() {
        configManager.save();
    }

    @Override
    public Screen create(Screen parent) {
        var builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(translatable("notica.config.title"))
                .setSavingRunnable(this::save);

        ConfigCategory client = builder.getOrCreateCategory(translatable("notica.config.client"));

        initCategory(builder, client, config, defaultConfig);

        return builder.build();
    }

    private void initCategory(ConfigBuilder builder, ConfigCategory category, Object src, Object defaultSrc) {
        for (Field field : NoticaClientConfig.class.getDeclaredFields()) {
            if (Modifier.isTransient(field.getModifiers())) continue;

            var type = field.getType();
            String name = field.getName();
            String comment = ConfigManager.comment(field);

            if (!ConfigOption.isValue(type)) {
                continue;
            }

            var option = new ConfigOption(field, NoticaClientConfig.class);
            Object value = option.get(src);
            Object defaultValue = option.get(defaultSrc);

            if (value == null || defaultValue == null) continue;

            var label = translatable(optionTitleKey(name));

            var tooltip = comment != null
                    ? translatableWithFallback(optionDescKey(name), comment)
                    : null;

            ConfigSlider sliderAnnotation = field.getAnnotation(ConfigSlider.class);
            AbstractConfigListEntry<?> entry;

            if (sliderAnnotation != null && type == double.class) {
                entry = sliderEntry(builder, label, tooltip, value, defaultValue, v -> option.set(src, v), sliderAnnotation);
            } else {
                entry = entry(new EntryData(builder, type, value, defaultValue, v -> option.set(src, v), label, name, tooltip));
            }

            if (entry != null) {
                category.addEntry(entry);
            }
        }
    }

    private @Nullable AbstractConfigListEntry<?> entry(EntryData data) {
        if (data.type == boolean.class) {
            return data.builder.entryBuilder()
                    .startBooleanToggle(data.label, data.value instanceof Boolean b && b)
                    .setDefaultValue(data.defaultValue instanceof Boolean b && b)
                    .setTooltip(data.tooltip)
                    .setSaveConsumer(data.saveConsumer::accept)
                    .build();
        }

        if (data.type == double.class) {
            double dd = data.defaultValue instanceof Number n ? n.doubleValue() : 0.d;

            return data.builder.entryBuilder()
                    .startDoubleField(data.label, data.value instanceof Number n ? n.doubleValue() : dd)
                    .setDefaultValue(dd)
                    .setTooltip(data.tooltip)
                    .setSaveConsumer(data.saveConsumer::accept)
                    .build();
        }

        if (data.type == int.class) {
            int di = data.defaultValue instanceof Number n ? n.intValue() : 0;

            return data.builder.entryBuilder()
                    .startIntField(data.label, data.value instanceof Number n ? n.intValue() : di)
                    .setDefaultValue(di)
                    .setTooltip(data.tooltip)
                    .setSaveConsumer(data.saveConsumer::accept)
                    .build();
        }

        if (data.type.isEnum()) {
            return enumSelector(data);
        }

        return null;
    }

    private AbstractConfigListEntry<?> sliderEntry(
            ConfigBuilder builder, Component label, @Nullable Component tooltip,
            Object value, Object defaultValue,
            Consumer<Object> saveConsumer, ConfigSlider slider) {

        long factor = slider.factor();
        long current = value instanceof Number n ? (long) (n.doubleValue() * factor) : slider.min();
        long def = defaultValue instanceof Number n ? (long) (n.doubleValue() * factor) : slider.min();

        return builder.entryBuilder()
                .startLongSlider(label, current, slider.min(), slider.max())
                .setDefaultValue(def)
                .setTooltip(tooltip)
                .setTextGetter(v -> Component.literal(String.format("%.2f", (double) v / factor)))
                .setSaveConsumer(v -> saveConsumer.accept((double) v / factor))
                .build();
    }

    // convince the compiler that some class is an enum and that the value is an enum constant of it 💀💀💀
    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> EnumListEntry<?> enumSelector(EntryData data) {
        return data.builder.entryBuilder()
                .startEnumSelector(data.label, (Class<T>) data.type(), (T) data.value)
                .setDefaultValue((T) data.defaultValue)
                .setTooltipSupplier(val -> {
                    Field field;

                    try {
                        field = data.type().getField(val.name());
                    } catch (NoSuchFieldException e) {
                        return Optional.ofNullable(data.tooltip).map(t -> new Component[]{t});
                    }

                    String comment = ConfigManager.comment(field);

                    if (comment == null) {
                        return Optional.ofNullable(data.tooltip).map(t -> new Component[]{t});
                    }

                    Component desc = data.enumName(val)
                            .append(": ")
                            .append(translatableWithFallback(enumDescKey(val, data.path), comment));

                    return data.tooltip == null
                            ? Optional.of(new Component[]{desc})
                            : Optional.of(new Component[]{data.tooltip, desc});
                })
                .setEnumNameProvider(data::enumName)
                .setSaveConsumer(data.saveConsumer::accept)
                .build();
    }

    private record EntryData(
            ConfigBuilder builder,
            Class<?> type, Object value,
            Object defaultValue,
            Consumer<Object> saveConsumer,
            Component label,
            String path,
            @Nullable Component tooltip) {

        public MutableComponent enumName(Enum<?> val) {
            return translatableWithFallback(enumNameKey(val, path), val.name());
        }
    }
}
