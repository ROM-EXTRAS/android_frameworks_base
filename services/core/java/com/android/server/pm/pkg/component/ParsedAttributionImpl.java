/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.pm.pkg.component;

import android.annotation.NonNull;
import android.annotation.StringRes;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.pm.pkg.component.ParsedAttribution;
import com.android.internal.util.DataClass;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link android.R.styleable#AndroidManifestAttribution &lt;attribution&gt;} tag parsed from the
 * manifest.
 *
 * @hide
 */
@DataClass(genAidl = false, genSetters = true, genBuilder = false, genParcelable = true)
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ParsedAttributionImpl implements ParsedAttribution, Parcelable {

    /** Maximum amount of attributions per package */
    static final int MAX_NUM_ATTRIBUTIONS = 10000;

    /** Tag of the attribution */
    private @NonNull String tag;

    /** User visible label fo the attribution */
    private @StringRes int label;

    /** Ids of previously declared attributions this attribution inherits from */
    private @NonNull List<String> inheritFrom;

    public ParsedAttributionImpl() {}



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/core/java/android/content/pm/parsing/component/ParsedAttributionImpl.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Creates a new ParsedAttributionImpl.
     *
     * @param tag
     *   Tag of the attribution
     * @param label
     *   User visible label fo the attribution
     * @param inheritFrom
     *   Ids of previously declared attributions this attribution inherits from
     */
    @DataClass.Generated.Member
    public ParsedAttributionImpl(
            @NonNull String tag,
            @StringRes int label,
            @NonNull List<String> inheritFrom) {
        this.tag = tag;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, tag);
        this.label = label;
        com.android.internal.util.AnnotationValidations.validate(
                StringRes.class, null, label);
        this.inheritFrom = inheritFrom;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, inheritFrom);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Tag of the attribution
     */
    @DataClass.Generated.Member
    public @NonNull String getTag() {
        return tag;
    }

    /**
     * User visible label fo the attribution
     */
    @DataClass.Generated.Member
    public @StringRes int getLabel() {
        return label;
    }

    /**
     * Ids of previously declared attributions this attribution inherits from
     */
    @DataClass.Generated.Member
    public @NonNull List<String> getInheritFrom() {
        return inheritFrom;
    }

    /**
     * Tag of the attribution
     */
    @DataClass.Generated.Member
    public @NonNull ParsedAttributionImpl setTag(@NonNull String value) {
        tag = value;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, tag);
        return this;
    }

    /**
     * User visible label fo the attribution
     */
    @DataClass.Generated.Member
    public @NonNull ParsedAttributionImpl setLabel(@StringRes int value) {
        label = value;
        com.android.internal.util.AnnotationValidations.validate(
                StringRes.class, null, label);
        return this;
    }

    /**
     * Ids of previously declared attributions this attribution inherits from
     */
    @DataClass.Generated.Member
    public @NonNull ParsedAttributionImpl setInheritFrom(@NonNull List<String> value) {
        inheritFrom = value;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, inheritFrom);
        return this;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeString(tag);
        dest.writeInt(label);
        dest.writeStringList(inheritFrom);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    protected ParsedAttributionImpl(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        String _tag = in.readString();
        int _label = in.readInt();
        List<String> _inheritFrom = new ArrayList<>();
        in.readStringList(_inheritFrom);

        this.tag = _tag;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, tag);
        this.label = _label;
        com.android.internal.util.AnnotationValidations.validate(
                StringRes.class, null, label);
        this.inheritFrom = _inheritFrom;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, inheritFrom);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<ParsedAttributionImpl> CREATOR
            = new Parcelable.Creator<ParsedAttributionImpl>() {
        @Override
        public ParsedAttributionImpl[] newArray(int size) {
            return new ParsedAttributionImpl[size];
        }

        @Override
        public ParsedAttributionImpl createFromParcel(@NonNull Parcel in) {
            return new ParsedAttributionImpl(in);
        }
    };

    @DataClass.Generated(
            time = 1641431950829L,
            codegenVersion = "1.0.23",
            sourceFile = "frameworks/base/core/java/android/content/pm/parsing/component/ParsedAttributionImpl.java",
            inputSignatures = "static final  int MAX_NUM_ATTRIBUTIONS\nprivate @android.annotation.NonNull java.lang.String tag\nprivate @android.annotation.StringRes int label\nprivate @android.annotation.NonNull java.util.List<java.lang.String> inheritFrom\nclass ParsedAttributionImpl extends java.lang.Object implements [android.content.pm.parsing.component.ParsedAttribution, android.os.Parcelable]\n@com.android.internal.util.DataClass(genAidl=false, genSetters=true, genBuilder=false, genParcelable=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
