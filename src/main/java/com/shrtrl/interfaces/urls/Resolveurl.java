package com.shrtrl.interfaces.urls;

import com.shrtrl.models.Urls;


public sealed interface Resolveurl 
{

    record shortcodeNotFound() implements Resolveurl{};
    record shortcodeFound(Urls url) implements Resolveurl{};
}
