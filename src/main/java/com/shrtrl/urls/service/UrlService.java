package com.shrtrl.urls.service;

@Service
public class UrlService 
{
    private UrlRepository urlRepository;

    public void save(Url url)
    {
        urlRepository.save(url);
    }

    public List<Url> getUrls()
    {
        return urlRepository.findAll();
    }
    

}
