package com.astral.exchange;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {
    @GetMapping({"/assets", "/ranking", "/challenge"})
    String frontendRoute() { return "forward:/index.html"; }
}
