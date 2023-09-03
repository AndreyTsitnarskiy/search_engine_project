package searchengine.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class Properties {

    @Value("${connect.user-agent}")
    private String userAgent;

    @Value("${connect.referrer}")
    private String referrer;

    @Value("${snippet.border}")
    private int snippetBorder;

    @Value("${file.extensions}")
    private String fileExtensions;

    @Value("${selector.weight.title}")
    private float weightTitle;

    @Value("${selector.weight.body}")
    private float weightBody;

    @Value("${error.interrupted}")
    private String interruptedByUserMessage;

    @Value("${error.certificate}")
    private String certificateError;

    @Value("${error.unknown}")
    private String unknownError;

    @Value("${page.frequency.limit}")
    private int pageFrequencyLimit;
}
